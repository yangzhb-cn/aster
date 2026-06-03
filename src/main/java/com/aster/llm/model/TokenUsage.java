package com.aster.llm.model;

/**
 * 一次 LLM 请求的真实 token 用量。
 *
 * <p>这里记录的是供应商 API 返回的 usage，不是 ContextBuilder 的估算值。
 * 输入缓存命中 token 只有部分供应商会返回，例如 DeepSeek 的
 * prompt_cache_hit_tokens / prompt_cache_miss_tokens。</p>
 */
public record TokenUsage(
        int inputTokens,
        int inputCacheTokens,
        int inputCacheMissTokens,
        int outputTokens,
        int totalTokens
) {
    /**
     * 从流式响应最后的 usage chunk 转成项目内部模型。
     */
    public static TokenUsage from(ChatStreamChunk.Usage usage) {
        // DeepSeek 的语义里，prompt token 可以继续拆成：
        // 缓存命中的输入 token + 未命中的输入 token。
        // 如果供应商返回了这两个字段，展示层就按这个公式计算 input，
        // 避免 prompt_tokens 与 cache/miss 混用时看起来“对不上”。
        int cacheTokens = usage.promptCacheHitTokens();
        int cacheMissTokens = usage.promptCacheMissTokens();
        int inputTokens = cacheTokens + cacheMissTokens;
        if (inputTokens == 0) {
            inputTokens = usage.promptTokens();
        }

        int outputTokens = usage.completionTokens();
        int totalTokens = usage.totalTokens();
        if (cacheTokens + cacheMissTokens > 0 || totalTokens == 0) {
            totalTokens = inputTokens + outputTokens;
        }

        return new TokenUsage(
                inputTokens,
                cacheTokens,
                cacheMissTokens,
                outputTokens,
                totalTokens
        );
    }
}
