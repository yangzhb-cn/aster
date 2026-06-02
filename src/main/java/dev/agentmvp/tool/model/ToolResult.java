package dev.agentmvp.tool.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具执行结果。
 *
 * <p>它还不是聊天消息。AgentLoop 会把 ToolResult 渲染成 role=tool 的 Message，
 * 并带上 toolCallId，保证和 assistant.tool_calls 配对。</p>
 */
public record ToolResult(
        String toolCallId,
        String toolName,
        boolean error,
        long elapsedMillis,
        List<ToolContent> contents
) {
    public ToolResult(String toolCallId, boolean error, List<ToolContent> contents) {
        this(toolCallId, null, error, 0, contents);
    }

    /**
     * 创建成功的文本工具结果。
     */
    public static ToolResult text(String toolCallId, String text) {
        return new ToolResult(toolCallId, null, false, 0, List.of(ToolContent.text(text)));
    }

    /**
     * 创建失败的工具结果。失败也要返回 tool 消息，否则 LLM 协议会断。
     */
    public static ToolResult error(String toolCallId, String message) {
        return new ToolResult(toolCallId, null, true, 0, List.of(ToolContent.text(message)));
    }

    /**
     * 补上工具名和耗时。
     *
     * <p>具体工具处理器只关心业务结果；并行执行器统一记录耗时，
     * 再用这个方法补成 TUI 可以展示的完整结果。</p>
     */
    public ToolResult withExecutionMetadata(String toolName, long elapsedMillis) {
        return new ToolResult(toolCallId, toolName, error, elapsedMillis, contents);
    }

    /**
     * 把内容块渲染成发给 LLM 的纯文本。
     */
    public String renderText() {
        String text = contents.stream()
                .filter(content -> "text".equals(content.type()))
                .map(ToolContent::text)
                .collect(Collectors.joining("\n\n"));
        return error ? "Tool execution failed:\n" + text : text;
    }
}
