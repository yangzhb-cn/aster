package com.aster.llm.text.openai;

import com.aster.llm.text.deepseek.DeepSeekProvider;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.llm.text.ollama.OllamaProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        List<OpenAiCompatibleProviderDefinition> definitions = List.of(
                providerDefinition,
                new OllamaProvider()
        );
        String providerName = envOrDefault(PROVIDER_ENV, providerDefinition.name());
        OpenAiCompatibleProviderDefinition selectedDefinition = findDefinition(definitions, providerName)
                .orElse(providerDefinition);
        String baseUrl = envOrDefault(BASE_URL_ENV, selectedDefinition.defaultBaseUrl());
        String model = envOrDefault(MODEL_ENV, selectedDefinition.defaultModel());
        boolean useSelectedProviderCapabilities = selectedDefinition.name().equalsIgnoreCase(providerName);

        // 优先使用通用 OpenAI 兼容环境变量；如果没有，再回退到供应商自己的环境变量。
        String apiKey = firstNonBlank(
                System.getenv(API_KEY_ENV),
                System.getenv(selectedDefinition.apiKeyEnvName())
        );

        return new OpenAiCompatibleProvider(
                providerName,
                baseUrl,
                apiKey,
                model,
                useSelectedProviderCapabilities && selectedDefinition.thinkingEnabled(),
                useSelectedProviderCapabilities ? selectedDefinition.reasoningEffort() : null,
                useSelectedProviderCapabilities ? selectedDefinition.apiKeyRequired() : true,
                switchableModels(
                        useSelectedProviderCapabilities ? selectedDefinition.switchableChatModels() : List.of(model),
                        model
                ),
                useSelectedProviderCapabilities ? selectedDefinition.capabilities() : providerDefinition.capabilities(),
                useSelectedProviderCapabilities ? selectedDefinition.streamUsageEnabled() : true
        );
    }

    private static Optional<OpenAiCompatibleProviderDefinition> findDefinition(
            List<OpenAiCompatibleProviderDefinition> definitions,
            String providerName
    ) {
        String normalized = providerName.toLowerCase(Locale.ROOT);
        return definitions.stream()
                .filter(definition -> definition.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    private static List<String> switchableModels(List<String> configuredModels, String defaultModel) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (defaultModel != null && !defaultModel.isBlank()) {
            models.add(defaultModel);
        }
        for (String configuredModel : configuredModels) {
            if (configuredModel != null && !configuredModel.isBlank()) {
                models.add(configuredModel);
            }
        }
        return List.copyOf(models);
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
