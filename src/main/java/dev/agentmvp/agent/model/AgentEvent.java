package dev.agentmvp.agent.model;

import dev.agentmvp.llm.model.TokenUsage;

/**
 * 流式 Agent 主循环发出的事件。
 *
 * <p>TUI 可以收到 token 事件就立即追加到输出区。
 * 以后如果加 Web SSE，也可以直接转发这些事件，不需要改 AgentLoop。</p>
 */
public sealed interface AgentEvent permits AgentEvent.AssistantToken, AgentEvent.ReasoningToken, AgentEvent.ToolCallStart, AgentEvent.ToolCallDone, AgentEvent.UsageReported, AgentEvent.Done {
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
     * 一次 LLM 请求结束后返回的真实 token 用量。
     *
     * <p>这是模型供应商返回的 usage。它和 ContextBuilder 的 token 估算不同，
     * 可以用于观察每轮输入、缓存命中、输出和总 token。
     * maxContextTokens 来自 ContextOptions，用来在界面上计算当前上下文窗口占比。</p>
     */
    record UsageReported(TokenUsage usage, int maxContextTokens) implements AgentEvent {
    }

    /**
     * 当前用户输入的一轮 Agent 执行结束。
     */
    record Done(String finalText) implements AgentEvent {
    }
}
