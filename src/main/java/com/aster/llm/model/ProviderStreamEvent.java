package com.aster.llm.model;

/**
 * 模型供应商流式输出的统一事件。
 *
 * <p>不同供应商的 SSE 形状不一样：OpenAI-compatible 是 choices/delta，
 * Anthropic、Google 也会有自己的字段。Parser 负责把各自格式转换成这一层，
 * AgentLoop 只处理 ProviderStreamEvent，不直接依赖某个供应商的原始 JSON。</p>
 */
public sealed interface ProviderStreamEvent permits
        ProviderStreamEvent.TextDelta,
        ProviderStreamEvent.ReasoningDelta,
        ProviderStreamEvent.ToolCallDeltaPart,
        ProviderStreamEvent.UsageDelta,
        ProviderStreamEvent.Done {
    /**
     * assistant 可见正文增量。
     */
    record TextDelta(String text) implements ProviderStreamEvent {
    }

    /**
     * 供应商返回的可展示推理增量。
     *
     * <p>DeepSeek 对应 reasoning_content。这里不是宿主程序自己生成的隐藏思维链，
     * 只是供应商 API 明确返回、允许展示的内容。</p>
     */
    record ReasoningDelta(String text) implements ProviderStreamEvent {
    }

    /**
     * 工具调用的一段增量。
     *
     * <p>函数名和 arguments JSON 可能被拆成多片返回，所以这里保留 delta，
     * 由 AssistantMessageBuilder 在流结束后拼成完整 ToolCall。</p>
     */
    record ToolCallDeltaPart(ToolCallDelta delta) implements ProviderStreamEvent {
    }

    /**
     * 供应商返回的真实 token 用量。
     */
    record UsageDelta(TokenUsage usage) implements ProviderStreamEvent {
    }

    /**
     * 供应商声明本次流式响应结束。
     */
    record Done() implements ProviderStreamEvent {
    }
}
