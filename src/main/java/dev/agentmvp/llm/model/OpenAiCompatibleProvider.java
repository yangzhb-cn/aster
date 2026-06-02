package dev.agentmvp.llm.model;

import java.util.Objects;

/**
 * 一个 OpenAI 协议兼容的模型供应商配置。
 *
 * <p>DeepSeek、Kimi、Step 这类供应商通常只是 baseUrl、apiKey、
 * defaultModel 不同，AgentLoop 不应该关心这些细节。</p>
 */
public record OpenAiCompatibleProvider(
        String name,
        String baseUrl,
        String apiKey,
        String defaultModel,
        boolean thinkingEnabled,
        String reasoningEffort
) {
    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String defaultModel) {
        this(name, baseUrl, apiKey, defaultModel, false, null);
    }

    public OpenAiCompatibleProvider {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(defaultModel, "defaultModel");
    }

    /**
     * 拼出 chat completions 端点，避免调用处到处手写 URL。
     */
    public String chatCompletionsEndpoint() {
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalized + "/chat/completions";
    }
}
