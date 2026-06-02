package dev.agentmvp.tui.render;

import com.googlecode.lanterna.TerminalTextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教学版轻量 Markdown 渲染器。
 *
 * <p>它不是完整 CommonMark 实现，只覆盖 TUI 中最常见、最有价值的格式：
 * 标题、引用、分隔线、代码块、表格、编号/列表、行内 code 和粗体标记清理。
 * 这样不引入额外依赖，也能让模型输出比纯文本更容易读。</p>
 */
public class MarkdownRenderer {
    private static final Pattern ORDERED_LIST = Pattern.compile("^(\\d+)[.)]\\s+(.*)$");
    private static final Pattern UNORDERED_LIST = Pattern.compile("^[-*+]\\s+(.*)$");

    private final TerminalTextSanitizer textSanitizer = new TerminalTextSanitizer();

    /**
     * 把 Markdown 文本转换成行级渲染结果。
     */
    public List<MarkdownLine> render(String markdown, int width) {
        String normalized = textSanitizer.sanitize(nullToEmpty(markdown))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);

        List<MarkdownLine> result = new ArrayList<>();
        boolean inCodeBlock = false;
        List<String> tableBuffer = new ArrayList<>();

        for (String rawLine : rawLines) {
            String trimmed = rawLine.trim();

            if (trimmed.startsWith("```")) {
                flushTable(result, tableBuffer);
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (inCodeBlock) {
                result.add(new MarkdownLine(rawLine, MarkdownLineType.CODE));
                continue;
            }

            if (looksLikeTableRow(trimmed)) {
                tableBuffer.add(rawLine);
                continue;
            }

            flushTable(result, tableBuffer);
            appendNormalMarkdownLine(result, rawLine, trimmed, width);
        }

        flushTable(result, tableBuffer);
        return result;
    }

    private void appendNormalMarkdownLine(List<MarkdownLine> result, String rawLine, String trimmed, int width) {
        if (trimmed.isEmpty()) {
            result.add(MarkdownLine.blank());
            return;
        }

        if (isRule(trimmed)) {
            result.add(new MarkdownLine("-".repeat(Math.max(3, width)), MarkdownLineType.RULE));
            return;
        }

        if (trimmed.startsWith(">")) {
            String quote = trimmed.replaceFirst("^>\\s?", "");
            result.add(new MarkdownLine("| " + cleanInlineMarkdown(quote), MarkdownLineType.QUOTE));
            return;
        }

        if (trimmed.matches("^#{1,6}\\s+.*")) {
            String heading = trimmed.replaceFirst("^#{1,6}\\s+", "");
            result.add(new MarkdownLine(cleanInlineMarkdown(heading), MarkdownLineType.HEADING));
            return;
        }

        Matcher orderedList = ORDERED_LIST.matcher(trimmed);
        if (orderedList.matches()) {
            result.add(new MarkdownLine(
                    orderedList.group(1) + ". " + cleanInlineMarkdown(orderedList.group(2)),
                    MarkdownLineType.LIST
            ));
            return;
        }

        Matcher unorderedList = UNORDERED_LIST.matcher(trimmed);
        if (unorderedList.matches()) {
            result.add(new MarkdownLine(
                    "• " + cleanInlineMarkdown(unorderedList.group(1)),
                    MarkdownLineType.LIST
            ));
            return;
        }

        result.add(new MarkdownLine(cleanInlineMarkdown(rawLine), MarkdownLineType.NORMAL));
    }

    private boolean looksLikeTableRow(String trimmed) {
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.indexOf('|', 1) > 0;
    }

    private void flushTable(List<MarkdownLine> result, List<String> tableBuffer) {
        if (tableBuffer.isEmpty()) {
            return;
        }

        List<List<String>> rows = tableBuffer.stream()
                .map(this::parseTableRow)
                .filter(row -> !isSeparatorRow(row))
                .toList();
        tableBuffer.clear();

        if (rows.isEmpty()) {
            return;
        }

        int columnCount = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] widths = new int[columnCount];
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], TerminalTextUtils.getColumnWidth(row.get(i)));
            }
        }

        result.add(new MarkdownLine(tableBorder(widths), MarkdownLineType.TABLE));
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            result.add(new MarkdownLine(formatTableRow(rows.get(rowIndex), widths), MarkdownLineType.TABLE));
            if (rowIndex == 0 && rows.size() > 1) {
                result.add(new MarkdownLine(tableBorder(widths), MarkdownLineType.TABLE));
            }
        }
        result.add(new MarkdownLine(tableBorder(widths), MarkdownLineType.TABLE));
    }

    private List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        String withoutEdges = trimmed.substring(1, trimmed.length() - 1);
        String[] cells = withoutEdges.split("\\|", -1);

        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cleanInlineMarkdown(cell.trim()));
        }
        return result;
    }

    private boolean isSeparatorRow(List<String> row) {
        return !row.isEmpty() && row.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private String formatTableRow(List<String> row, int[] widths) {
        StringBuilder result = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < row.size() ? row.get(i) : "";
            result.append(' ')
                    .append(padRight(cell, widths[i]))
                    .append(" |");
        }
        return result.toString();
    }

    private String tableBorder(int[] widths) {
        StringBuilder result = new StringBuilder("+");
        for (int width : widths) {
            result.append("-".repeat(width + 2)).append('+');
        }
        return result.toString();
    }

    private String padRight(String value, int width) {
        int padding = Math.max(0, width - TerminalTextUtils.getColumnWidth(value));
        return value + " ".repeat(padding);
    }

    private boolean isRule(String trimmed) {
        return trimmed.matches("^-{3,}$")
                || trimmed.matches("^_{3,}$")
                || trimmed.matches("^\\*{3,}$");
    }

    /**
     * 清理行内 Markdown 标记。
     *
     * <p>字符终端很难做同一行内的局部富文本。这里先把常见标记去掉：
     * `code` 显示为 code，**bold** 显示为 bold。emoji 和普通 Unicode 字符会原样保留。</p>
     */
    private String cleanInlineMarkdown(String text) {
        return nullToEmpty(text)
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
