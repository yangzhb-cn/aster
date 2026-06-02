package dev.agentmvp.llm;

import dev.agentmvp.llm.model.OpenAiCompatibleProvider;

/**
 * 从环境变量读取模型供应商配置。
 */
public final class OpenAiCompatibleProviderFactory {
    public static final String PROVIDER_ENV = "OPENAI_COMPATIBLE_PROVIDER";
    public static final String BASE_URL_ENV = "OPENAI_COMPATIBLE_BASE_URL";
    public static final String API_KEY_ENV = "OPENAI_COMPATIBLE_API_KEY";
    public static final String MODEL_ENV = "OPENAI_COMPATIBLE_MODEL";

    private OpenAiCompatibleProviderFactory() {
    }

    /**
     * 使用 DeepSeek 作为默认供应商定义。
     */
    public static OpenAiCompatibleProvider fromEnvWithDeepSeekDefaults() {
        return fromEnvWithDefaults(new DeepSeekProvider());
    }

    /**
     * 根据某个供应商定义读取环境变量。
     *
     * <p>通用 OPENAI_COMPATIBLE_* 环境变量优先级最高。
     * 如果没有提供通用 API Key，再回退到供应商自己的 API Key 环境变量。</p>
     */
    public static OpenAiCompatibleProvider fromEnvWithDefaults(OpenAiCompatibleProviderDefinition providerDefinition) {
        String providerName = envOrDefault(PROVIDER_ENV, providerDefinition.name());
        String baseUrl = envOrDefault(BASE_URL_ENV, providerDefinition.defaultBaseUrl());
        String model = envOrDefault(MODEL_ENV, providerDefinition.defaultModel());
        boolean useDefaultProviderCapabilities = providerDefinition.name().equalsIgnoreCase(providerName);

        // 优先使用通用 OpenAI 兼容环境变量；如果没有，再回退到供应商自己的环境变量。
        String apiKey = firstNonBlank(
                System.getenv(API_KEY_ENV),
                System.getenv(providerDefinition.apiKeyEnvName())
        );

        return new OpenAiCompatibleProvider(
                providerName,
                baseUrl,
                apiKey,
                model,
                useDefaultProviderCapabilities && providerDefinition.thinkingEnabled(),
                useDefaultProviderCapabilities ? providerDefinition.reasoningEffort() : null
        );
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
