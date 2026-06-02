package dev.agentmvp.tui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import dev.agentmvp.runtime.AgentRuntime;
import dev.agentmvp.tui.model.AssistantBlock;
import dev.agentmvp.tui.model.ErrorBlock;
import dev.agentmvp.tui.model.ReasoningBlock;
import dev.agentmvp.tui.model.SystemBlock;
import dev.agentmvp.tui.model.ToolBlock;
import dev.agentmvp.tui.model.ToolStatus;
import dev.agentmvp.tui.model.UiBlock;
import dev.agentmvp.tui.model.UserBlock;
import dev.agentmvp.tui.render.MarkdownLine;
import dev.agentmvp.tui.render.MarkdownLineType;
import dev.agentmvp.tui.render.MarkdownRenderer;
import dev.agentmvp.tui.render.TerminalTextSanitizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 最小化的手绘 TUI。
 *
 * <p>这个类只负责展示，不直接理解 Agent 业务。
 * AgentLoop 发来的事件会被转换成 UiBlock：
 * 用户块、reasoning 块、工具块、assistant 正文块，再统一渲染。</p>
 */
public class AgentTuiWindow implements AutoCloseable {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_BLOCKS = 500;

    private static final TextColor BG = new TextColor.RGB(37, 37, 37);
    private static final TextColor TOOL_BG = new TextColor.RGB(32, 47, 36);
    private static final TextColor CODE_BG = new TextColor.RGB(42, 42, 48);
    private static final TextColor TEXT = new TextColor.RGB(215, 210, 190);
    private static final TextColor MUTED = new TextColor.RGB(126, 126, 126);
    private static final TextColor CYAN = new TextColor.RGB(139, 190, 181);
    private static final TextColor YELLOW = new TextColor.RGB(232, 190, 104);
    private static final TextColor PURPLE = new TextColor.RGB(214, 123, 245);
    private static final TextColor ERROR = new TextColor.RGB(226, 92, 92);

    private final Screen screen;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();
    private final TerminalTextSanitizer textSanitizer = new TerminalTextSanitizer();
    private final ExecutorService agentExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<Runnable> uiUpdates = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<UiBlock> blocks = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();

    private AgentRuntime runtime;
    private String status = "initializing";
    private int scrollOffset;
    private int lastHistoryHeight = 1;
    private int lastMaxScrollOffset;
    private boolean closed;
    private boolean dirty = true;

    public AgentTuiWindow(Screen screen) {
        this.screen = Objects.requireNonNull(screen);
    }

    /**
     * 注入 Agent 运行时。
     */
    public void setRuntime(AgentRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        status = runtime.provider().thinkingEnabled()
                ? "ready | thinking=" + runtime.provider().reasoningEffort()
                : "ready";
        dirty = true;
    }

    /**
     * 设置底部状态栏文本。
     */
    public void setStatus(String status) {
        this.status = Objects.requireNonNull(status);
        dirty = true;
    }

    /**
     * 进入 TUI 事件循环。
     */
    public void run() throws IOException {
        redraw();
        while (!closed) {
            processResize();
            processInput();
            drainUiUpdates();
            if (dirty) {
                redraw();
            }
            sleepQuietly();
        }
    }

    /**
     * 追加系统提示信息。
     */
    public void appendSystemLine(String text) {
        enqueue(() -> {
            addBlock(new SystemBlock(text));
            dirty = true;
        });
    }

    /**
     * 追加 assistant 正文 token。
     */
    public void appendAssistantToken(String token) {
        enqueue(() -> {
            appendToAssistantBlock(token);
            dirty = true;
        });
    }

    /**
     * 追加 DeepSeek reasoning_content token。
     */
    public void appendReasoningToken(String token) {
        enqueue(() -> {
            appendToReasoningBlock(token);
            dirty = true;
        });
    }

    /**
     * 显示工具开始事件。
     */
    public void showToolStart(String toolCallId, String toolName, String argumentsJson) {
        enqueue(() -> {
            addBlock(new ToolBlock(toolCallId, toolName, argumentsJson));
            status = "running tool: " + toolName;
            dirty = true;
        });
    }

    /**
     * 显示工具完成事件。
     */
    public void showToolDone(String toolCallId, String toolName, String text, boolean success, long elapsedMillis) {
        enqueue(() -> {
            ToolBlock block = findToolBlock(toolCallId);
            if (block == null) {
                block = new ToolBlock(toolCallId, toolName, "");
                addBlock(block);
            }
            block.finish(text, success, elapsedMillis);
            status = success ? "tool done: " + toolName : "tool failed: " + toolName;
            dirty = true;
        });
    }

    /**
     * 显示本轮回答结束。
     */
    public void showDone() {
        enqueue(() -> {
            status = "ready";
            dirty = true;
        });
    }

    /**
     * 关闭窗口和后台执行器。
     */
    @Override
    public void close() {
        closed = true;
        agentExecutor.shutdownNow();
    }

    private void processInput() throws IOException {
        KeyStroke key;
        while ((key = screen.pollInput()) != null) {
            handleKey(key);
        }
    }

    private void handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape || isCtrl(key, 'c') || isCtrl(key, 'd')) {
            close();
            return;
        }
        if (isCtrl(key, 'l')) {
            blocks.clear();
            scrollOffset = 0;
            status = "cleared";
            dirty = true;
            return;
        }
        if (isCtrl(key, 'o')) {
            toggleToolFolding();
            return;
        }
        if (handleScrollKey(key)) {
            return;
        }
        if (key.getKeyType() == KeyType.Backspace && !input.isEmpty()) {
            input.deleteCharAt(input.length() - 1);
            dirty = true;
            return;
        }
        if (key.getKeyType() == KeyType.Enter) {
            sendCurrentInput();
            return;
        }
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            input.append(key.getCharacter());
            dirty = true;
        }
    }

    private boolean isCtrl(KeyStroke key, char value) {
        return key.isCtrlDown()
                && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == value;
    }

    private void sendCurrentInput() {
        String userInput = input.toString().trim();
        if (userInput.isEmpty()) {
            status = "input is empty";
            dirty = true;
            return;
        }
        if (handleSlashCommand(userInput)) {
            return;
        }

        input.setLength(0);
        scrollOffset = 0;
        addBlock(new UserBlock(userInput));
        dirty = true;

        if (runtime == null) {
            status = "runtime is not ready";
            addBlock(new ErrorBlock("runtime is not ready"));
            dirty = true;
            return;
        }
        if (!running.compareAndSet(false, true)) {
            status = "agent is already running";
            addBlock(new ErrorBlock("agent is already running"));
            dirty = true;
            return;
        }

        status = "running";
        dirty = true;

        agentExecutor.submit(() -> {
            try {
                runtime.run(userInput);
            } catch (Exception e) {
                enqueue(() -> {
                    addBlock(new ErrorBlock(e.getMessage()));
                    status = "error: " + e.getMessage();
                    dirty = true;
                });
            } finally {
                running.set(false);
            }
        });
    }

    /**
     * 处理教学版先支持的唯一斜杠命令。
     */
    private boolean handleSlashCommand(String userInput) {
        if (!"/exit".equals(userInput)) {
            return false;
        }
        input.setLength(0);
        status = "exiting";
        dirty = true;
        close();
        return true;
    }

    /**
     * 处理历史消息滚动。
     */
    private boolean handleScrollKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowUp) {
            scrollHistory(1);
            return true;
        }
        if (key.getKeyType() == KeyType.ArrowDown) {
            scrollHistory(-1);
            return true;
        }
        if (key.getKeyType() == KeyType.PageUp) {
            scrollHistory(lastHistoryHeight);
            return true;
        }
        if (key.getKeyType() == KeyType.PageDown) {
            scrollHistory(-lastHistoryHeight);
            return true;
        }
        if (key.getKeyType() == KeyType.End) {
            scrollOffset = 0;
            dirty = true;
            return true;
        }
        return false;
    }

    /**
     * 调整历史区滚动位置。
     */
    private void scrollHistory(int delta) {
        int maxOffset = Math.max(lastMaxScrollOffset, scrollOffset);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
        dirty = true;
    }

    /**
     * 展开或折叠所有已有工具输出。
     *
     * <p>教学版没有复杂的“当前选中工具块”，所以先做全局切换。
     * 只要存在一个折叠工具，就执行展开；否则把所有有输出的工具折叠。</p>
     */
    private void toggleToolFolding() {
        List<ToolBlock> tools = blocks.stream()
                .filter(block -> block instanceof ToolBlock)
                .map(block -> (ToolBlock) block)
                .filter(ToolBlock::collapsible)
                .toList();
        if (tools.isEmpty()) {
            status = "no tool output to fold";
            dirty = true;
            return;
        }

        boolean expand = tools.stream().anyMatch(ToolBlock::collapsed);
        for (ToolBlock tool : tools) {
            tool.setCollapsed(!expand);
        }
        status = expand ? "expanded tool outputs" : "collapsed tool outputs";
        dirty = true;
    }

    private void drainUiUpdates() {
        Runnable update;
        while ((update = uiUpdates.poll()) != null) {
            update.run();
        }
    }

    private void enqueue(Runnable update) {
        uiUpdates.add(update);
    }

    private void processResize() {
        TerminalSize resized = screen.doResizeIfNecessary();
        if (resized != null) {
            dirty = true;
        }
    }

    private void redraw() throws IOException {
        TerminalSize size = screen.getTerminalSize();
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setBackgroundColor(BG).setForegroundColor(TEXT).clearModifiers().fill(' ');

        drawHeader(graphics, size);
        drawHistory(graphics, size);
        drawInput(graphics, size);

        screen.refresh();
        dirty = false;
    }

    private void drawHeader(TextGraphics graphics, TerminalSize size) {
        int width = size.getColumns();
        putClipped(graphics, 1, 0, "agent-small-mvp", Math.max(1, width - 2), CYAN, BG, SGR.BOLD);
        drawHorizontalLine(graphics, 1, width, PURPLE);
    }

    private void drawHistory(TextGraphics graphics, TerminalSize size) {
        int startY = 3;
        int endY = Math.max(startY, size.getRows() - 6);
        int height = endY - startY;
        if (height <= 0) {
            return;
        }

        List<RenderedLine> lines = renderBlocks(Math.max(10, size.getColumns() - 4));
        lastHistoryHeight = height;
        lastMaxScrollOffset = Math.max(0, lines.size() - height);
        scrollOffset = Math.min(scrollOffset, lastMaxScrollOffset);

        int from = Math.max(0, lines.size() - height - scrollOffset);
        int to = Math.min(lines.size(), from + height);
        int y = startY;
        for (int i = from; i < to && y < endY; i++) {
            RenderedLine line = lines.get(i);
            putLine(graphics, 2, y++, line, Math.max(10, size.getColumns() - 4));
        }
    }

    private void drawInput(TextGraphics graphics, TerminalSize size) {
        int width = size.getColumns();
        int statusY = Math.max(0, size.getRows() - 5);
        int topLineY = Math.max(0, size.getRows() - 4);
        int inputY = Math.max(0, size.getRows() - 3);
        int bottomLineY = Math.max(0, size.getRows() - 2);

        String visibleStatus = scrollOffset == 0 ? status : status + " | scroll +" + scrollOffset + " | End 到底部";
        putClipped(graphics, 1, statusY, visibleStatus, Math.max(1, width - 2), MUTED, BG);
        drawHorizontalLine(graphics, topLineY, width, PURPLE);
        drawHorizontalLine(graphics, bottomLineY, width, PURPLE);

        put(graphics, 2, inputY, "> ", MUTED, BG);

        int inputWidth = Math.max(1, width - 6);
        String visibleInput = tailToColumnWidth(textSanitizer.sanitize(input.toString()), inputWidth);
        putClipped(graphics, 4, inputY, visibleInput, inputWidth, TEXT, BG);

        int cursorX = Math.min(4 + TerminalTextUtils.getColumnWidth(visibleInput), width - 1);
        screen.setCursorPosition(new TerminalPosition(cursorX, inputY));
    }

    private List<RenderedLine> renderBlocks(int width) {
        List<RenderedLine> lines = new ArrayList<>();
        for (UiBlock block : blocks) {
            int before = lines.size();
            appendBlockLines(lines, block, width);
            if (lines.size() > before) {
                lines.add(RenderedLine.blank());
            }
        }
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void appendBlockLines(List<RenderedLine> lines, UiBlock block, int width) {
        if (block instanceof UserBlock user) {
            appendTextLines(lines, "user: " + user.text(), width, CYAN, BG);
            return;
        }
        if (block instanceof AssistantBlock assistant) {
            lines.add(new RenderedLine("assistant:", MUTED, BG, new SGR[]{SGR.BOLD}));
            appendMarkdownLines(lines, assistant.text(), width);
            return;
        }
        if (block instanceof ReasoningBlock reasoning) {
            appendTextLines(lines, "thinking: " + reasoning.text(), width, MUTED, BG, SGR.ITALIC);
            return;
        }
        if (block instanceof ToolBlock tool) {
            appendToolLines(lines, tool, width);
            return;
        }
        if (block instanceof SystemBlock system) {
            appendTextLines(lines, "system: " + system.text(), width, MUTED, BG);
            return;
        }
        if (block instanceof ErrorBlock error) {
            appendTextLines(lines, "error: " + error.text(), width, ERROR, BG);
        }
    }

    private void appendToolLines(List<RenderedLine> lines, ToolBlock tool, int width) {
        appendTextLines(lines, formatToolHeader(tool), width, TEXT, TOOL_BG, SGR.BOLD);

        if (!tool.outputText().isBlank()) {
            if (tool.collapsed()) {
                appendTextLines(lines, foldedToolSummary(tool), width, MUTED, TOOL_BG);
            } else {
                appendTextLines(lines, tool.outputText(), width, MUTED, TOOL_BG);
            }
        }

        if (tool.status() == ToolStatus.RUNNING) {
            appendTextLines(lines, "Running...", width, MUTED, TOOL_BG);
            return;
        }

        String took = "Took " + String.format("%.1fs", tool.elapsedMillis() / 1000.0);
        TextColor color = tool.status() == ToolStatus.FAILED ? ERROR : MUTED;
        appendTextLines(lines, took, width, color, TOOL_BG);
    }

    private String foldedToolSummary(ToolBlock tool) {
        long lineCount = tool.outputText().lines().count();
        int charCount = tool.outputText().length();
        return "... folded " + lineCount + " lines / " + charCount + " chars, ctrl+o expand";
    }

    private String formatToolHeader(ToolBlock tool) {
        Map<String, Object> arguments = parseToolArguments(tool.argumentsJson());
        return switch (tool.toolName()) {
            case "bash" -> "$ " + stringArg(arguments, "command", tool.argumentsJson());
            case "read" -> "read " + stringArg(arguments, "path", tool.argumentsJson());
            case "write" -> "write " + stringArg(arguments, "path", tool.argumentsJson());
            case "edit" -> "edit " + stringArg(arguments, "path", tool.argumentsJson());
            case "load_skill" -> "load_skill " + stringArg(arguments, "name", tool.argumentsJson());
            default -> tool.toolName() + " " + nullToEmpty(tool.argumentsJson());
        };
    }

    private Map<String, Object> parseToolArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringArg(Map<String, Object> arguments, String name, String fallback) {
        Object value = arguments.get(name);
        return value == null ? nullToEmpty(fallback) : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void appendTextLines(List<RenderedLine> lines, String text, int width, TextColor foreground, TextColor background, SGR... modifiers) {
        String normalized = textSanitizer.sanitize(nullToEmpty(text)).replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\n", -1);
        for (String paragraph : paragraphs) {
            for (String line : wrapLine(paragraph, width)) {
                lines.add(new RenderedLine(line, foreground, background, modifiers));
            }
        }
    }

    /**
     * 渲染 assistant Markdown 正文。
     */
    private void appendMarkdownLines(List<RenderedLine> lines, String markdown, int width) {
        for (MarkdownLine line : markdownRenderer.render(markdown, width)) {
            appendMarkdownLine(lines, line, width);
        }
    }

    private void appendMarkdownLine(List<RenderedLine> lines, MarkdownLine line, int width) {
        MarkdownLineType type = line.type();
        if (type == MarkdownLineType.BLANK) {
            lines.add(RenderedLine.blank());
            return;
        }

        TextColor foreground = markdownForeground(type);
        TextColor background = markdownBackground(type);
        SGR[] modifiers = markdownModifiers(type);

            for (String wrapped : wrapLine(textSanitizer.sanitize(line.text()), width)) {
                lines.add(new RenderedLine(wrapped, foreground, background, modifiers));
            }
    }

    private TextColor markdownForeground(MarkdownLineType type) {
        return switch (type) {
            case HEADING -> YELLOW;
            case QUOTE -> MUTED;
            case CODE -> CYAN;
            case TABLE -> TEXT;
            case RULE -> PURPLE;
            case LIST -> TEXT;
            case NORMAL, BLANK -> TEXT;
        };
    }

    private TextColor markdownBackground(MarkdownLineType type) {
        return type == MarkdownLineType.CODE ? CODE_BG : BG;
    }

    private SGR[] markdownModifiers(MarkdownLineType type) {
        return switch (type) {
            case HEADING -> new SGR[]{SGR.BOLD};
            case QUOTE -> new SGR[]{SGR.ITALIC};
            default -> new SGR[0];
        };
    }

    private List<String> wrapLine(String line, int width) {
        if (line.isEmpty()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        int index = 0;
        while (index < line.length()) {
            int codePoint = line.codePointAt(index);
            String value = new String(Character.toChars(codePoint));
            int valueWidth = TerminalTextUtils.getColumnWidth(value);

            if (currentWidth > 0 && currentWidth + valueWidth > width) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }

            current.append(value);
            currentWidth += valueWidth;

            if (currentWidth >= width) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }

            index += Character.charCount(codePoint);
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void addBlock(UiBlock block) {
        blocks.add(block);
        if (scrollOffset > 0) {
            scrollOffset++;
        }
        while (blocks.size() > MAX_BLOCKS) {
            blocks.remove(0);
        }
    }

    private void appendToAssistantBlock(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        UiBlock last = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
        AssistantBlock assistant = last instanceof AssistantBlock block ? block : null;
        if (assistant == null) {
            assistant = new AssistantBlock();
            addBlock(assistant);
        }
        assistant.append(token);
    }

    private void appendToReasoningBlock(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        UiBlock last = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
        ReasoningBlock reasoning = last instanceof ReasoningBlock block ? block : null;
        if (reasoning == null) {
            reasoning = new ReasoningBlock();
            addBlock(reasoning);
        }
        reasoning.append(token);
    }

    private ToolBlock findToolBlock(String toolCallId) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            UiBlock block = blocks.get(i);
            if (block instanceof ToolBlock tool && Objects.equals(tool.toolCallId(), toolCallId)) {
                return tool;
            }
        }
        return null;
    }

    private void drawHorizontalLine(TextGraphics graphics, int y, int width, TextColor color) {
        graphics.setForegroundColor(color).setBackgroundColor(BG).clearModifiers();
        graphics.drawLine(1, y, Math.max(1, width - 2), y, '-');
    }

    private void putLine(TextGraphics graphics, int x, int y, RenderedLine line, int width) {
        graphics.setForegroundColor(line.foreground()).setBackgroundColor(line.background()).clearModifiers();
        graphics.putString(x, y, " ".repeat(Math.max(0, width)));
        putClipped(graphics, x, y, line.text(), width, line.foreground(), line.background(), line.modifiers());
    }

    private void put(TextGraphics graphics, int x, int y, String text, TextColor foreground, TextColor background, SGR... modifiers) {
        graphics.setForegroundColor(foreground).setBackgroundColor(background).setModifiers(java.util.EnumSet.noneOf(SGR.class));
        if (modifiers.length > 0) {
            graphics.enableModifiers(modifiers);
        }
        graphics.putString(x, y, text);
        graphics.clearModifiers();
    }

    private void putClipped(TextGraphics graphics, int x, int y, String text, int width, TextColor foreground, TextColor background, SGR... modifiers) {
        if (width <= 0) {
            return;
        }
        String clipped = clipToColumnWidth(textSanitizer.sanitize(text), width);
        put(graphics, x, y, clipped, foreground, background, modifiers);
    }

    /**
     * 按终端列宽截断字符串。
     */
    private String clipToColumnWidth(String text, int maxColumns) {
        StringBuilder clipped = new StringBuilder();
        int columns = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            String value = new String(Character.toChars(codePoint));
            int valueWidth = TerminalTextUtils.getColumnWidth(value);
            if (columns + valueWidth > maxColumns) {
                break;
            }
            clipped.append(value);
            columns += valueWidth;
            index += Character.charCount(codePoint);
        }
        return clipped.toString();
    }

    /**
     * 保留字符串尾部，使它不超过指定终端列宽。
     */
    private String tailToColumnWidth(String text, int maxColumns) {
        StringBuilder tail = new StringBuilder();
        int columns = 0;
        int index = text.length();
        while (index > 0) {
            int codePoint = text.codePointBefore(index);
            String value = new String(Character.toChars(codePoint));
            int valueWidth = TerminalTextUtils.getColumnWidth(value);
            if (columns + valueWidth > maxColumns) {
                break;
            }
            tail.insert(0, value);
            columns += valueWidth;
            index -= Character.charCount(codePoint);
        }
        return tail.toString();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
        }
    }

    /**
     * TUI 绘制前展开出来的一行。
     */
    private record RenderedLine(String text, TextColor foreground, TextColor background, SGR[] modifiers) {
        static RenderedLine blank() {
            return new RenderedLine("", TEXT, BG, new SGR[0]);
        }
    }
}
