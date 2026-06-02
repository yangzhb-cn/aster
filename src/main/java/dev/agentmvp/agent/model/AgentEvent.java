package dev.agentmvp.agent.model;

/**
 * 流式 Agent 主循环发出的事件。
 *
 * <p>TUI 可以收到 token 事件就立即追加到输出区。
 * 以后如果加 Web SSE，也可以直接转发这些事件，不需要改 AgentLoop。</p>
 */
public sealed interface AgentEvent permits AgentEvent.AssistantToken, AgentEvent.ReasoningToken, AgentEvent.ToolCallStart, AgentEvent.ToolCallDone, AgentEvent.Done {
    /**
     * assistant 正文的流式 token。
     */
    record AssistantToken(String text) implements AgentEvent {
    }

    /**
     * DeepSeek thinking mode 返回的可展示推理摘要 token。
     *
     * <p>这里展示的是供应商 API 返回的 {@code reasoning_content}，
     * 不是宿主程序自己编造的隐藏思维链。</p>
     */
    record ReasoningToken(String text) implements AgentEvent {
    }

    /**
     * 工具调用开始，包含模型生成的原始 JSON 参数。
     */
    record ToolCallStart(String toolCallId, String toolName, String argumentsJson) implements AgentEvent {
    }

    /**
     * 工具调用完成，包含可展示结果和耗时。
     */
    record ToolCallDone(
            String toolCallId,
            String toolName,
            String text,
            boolean success,
            long elapsedMillis
    ) implements AgentEvent {
    }

    /**
     * 当前用户输入的一轮 Agent 执行结束。
     */
    record Done(String finalText) implements AgentEvent {
    }
}
