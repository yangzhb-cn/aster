package com.aster.llm.text.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI 兼容 SSE 返回的一片增量数据。
 *
 * <p>流式响应不是一次返回完整 assistant 消息，而是一段段增量。
 * AgentLoop 会把这些片段交给 AssistantMessageBuilder 逐步拼回完整消息。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatStreamChunk(
        List<Choice> choices,
        Usage usage
) {
    /**
     * 一次流式片段里的候选输出。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            ChatDelta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    /**
     * 真正的增量字段：可能是正文片段、thinking 片段，也可能是 tool_call 的局部字段。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatDelta(
            String role,
            String content,
            @JsonProperty("reasoning_content") String reasoningContent,
            @JsonProperty("tool_calls") List<ToolCallDelta> toolCalls
    ) {
    }

    /**
     * 流式响应最后一包可能携带的真实 token 用量。
     *
     * <p>OpenAI-compatible 通用字段是 prompt/completion/total。
     * DeepSeek 额外返回 prompt_cache_hit_tokens 和 prompt_cache_miss_tokens，
     * 用来展示输入缓存命中情况。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens,
            @JsonProperty("prompt_cache_hit_tokens") int promptCacheHitTokens,
            @JsonProperty("prompt_cache_miss_tokens") int promptCacheMissTokens
    ) {
    }
}
