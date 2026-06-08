package com.aster.llm.text.model;

import com.aster.llm.common.ModelCapability;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        String reasoningEffort,
        boolean apiKeyRequired,
        List<String> switchableChatModels,
        Set<ModelCapability> capabilities,
        boolean streamUsageEnabled
) {
    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String defaultModel) {
        this(name, baseUrl, apiKey, defaultModel, false, null);
    }

    public OpenAiCompatibleProvider(
            String name,
            String baseUrl,
            String apiKey,
            String defaultModel,
            boolean thinkingEnabled,
            String reasoningEffort
    ) {
        this(
                name,
                baseUrl,
                apiKey,
                defaultModel,
                thinkingEnabled,
                reasoningEffort,
                true,
                List.of(defaultModel),
                Set.of(ModelCapability.CHAT_COMPLETIONS),
                true
        );
    }

    public OpenAiCompatibleProvider {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(defaultModel, "defaultModel");
        switchableChatModels = List.copyOf(Objects.requireNonNull(switchableChatModels, "switchableChatModels"));
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
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

    /**
     * 判断供应商是否支持某种模型能力。
     */
    public boolean supports(ModelCapability capability) {
        return capabilities.contains(capability);
    }
}
