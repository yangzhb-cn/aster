package dev.agentmvp.agent.model;

import dev.agentmvp.llm.model.TokenUsage;

/**
 * 流式 Agent 主循环发出的事件。
 *
 * <p>事件本体只描述“发生了什么”。runId、sessionName、sequence、timestamp
 * 这些统一元信息由 AgentEventBus 包装成 AgentEventEnvelope 时补齐。</p>
 */
public sealed interface AgentEvent permits
        AgentEvent.RunStarted,
        AgentEvent.RunFinished,
        AgentEvent.RunFailed,
        AgentEvent.TurnStarted,
        AgentEvent.TurnFinished,
        AgentEvent.ContextBuilt,
        AgentEvent.LlmRequestStarted,
        AgentEvent.LlmRequestFinished,
        AgentEvent.MessageStarted,
        AgentEvent.MessageFinished,
        AgentEvent.AssistantToken,
        AgentEvent.ReasoningToken,
        AgentEvent.ToolCallStart,
        AgentEvent.ToolCallDone,
        AgentEvent.UsageReported,
        AgentEvent.Done {
    /**
     * 一次用户输入开始执行。
     *
     * <p>后续 Web SSE 或 hook 可以用它创建一条新的运行记录。</p>
     */
    record RunStarted(String userInput) implements AgentEvent {
    }

    /**
     * 一次用户输入成功结束。
     */
    record RunFinished(String finalText) implements AgentEvent {
    }

    /**
     * 一次用户输入异常结束。
     */
    record RunFailed(String errorMessage) implements AgentEvent {
    }

    /**
     * 一轮 LLM 请求开始。
     *
     * <p>一次 run 里可能有多轮：第一轮模型可能要求工具，
     * 工具结果写回后还会进入下一轮 LLM 请求。</p>
     */
    record TurnStarted(int round) implements AgentEvent {
    }

    /**
     * 一轮 LLM 请求结束。
     *
     * <p>reason 使用简单字符串，教学版先不引入额外枚举。
     * 当前常见值是 final、tool_calls。</p>
     */
    record TurnFinished(int round, String reason) implements AgentEvent {
    }

    /**
     * 上下文构造完成。
     *
     * <p>这让 TUI、Web、日志都能观察本轮是否触发压缩，以及压缩前后 token 估算。</p>
     */
    record ContextBuilt(
            boolean compressed,
            int beforeTokens,
            int afterTokens,
            int maxContextTokens
    ) implements AgentEvent {
    }

    /**
     * LLM 请求即将发出。
     */
    record LlmRequestStarted(
            int round,
            String model,
            int messageCount,
            int toolCount
    ) implements AgentEvent {
    }

    /**
     * LLM 请求已经结束。
     */
    record LlmRequestFinished(int round) implements AgentEvent {
    }

    /**
     * 一条消息开始产生。
     *
     * <p>role 可以是 user、assistant。tool 消息当前由 ToolCallDone 表达。</p>
     */
    record MessageStarted(int round, String role) implements AgentEvent {
    }

    /**
     * 一条消息已经完成。
     */
    record MessageFinished(int round, String role, boolean hasToolCalls) implements AgentEvent {
    }

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
