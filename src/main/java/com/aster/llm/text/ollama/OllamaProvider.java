package com.aster.llm.text.ollama;

import com.aster.llm.common.ModelCapability;
import com.aster.llm.text.openai.OpenAiCompatibleProviderDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ollama 本地模型供应商定义。
 *
 * <p>Ollama 的 chat 可以走 OpenAI-compatible {@code /v1/chat/completions}，
 * embedding 使用原生 {@code /api/embed}，因此这里同时声明 chat 和 embedding 能力。</p>
 */
public class OllamaProvider implements OpenAiCompatibleProviderDefinition {
    public static final String NAME = "ollama";
    public static final String BASE_URL_ENV = "OLLAMA_BASE_URL";
    public static final String CHAT_MODEL_ENV = "OLLAMA_CHAT_MODEL";
    public static final String CHAT_MODELS_ENV = "OLLAMA_CHAT_MODELS";
    public static final String EMBEDDING_MODEL_ENV = "OLLAMA_EMBEDDING_MODEL";
    public static final String API_KEY_ENV = "OLLAMA_API_KEY";

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_CHAT_MODEL = "qwen3:latest";
    public static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text:v1.5";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String defaultBaseUrl() {
        String baseUrl = normalizeBaseUrl(envOrDefault(BASE_URL_ENV, DEFAULT_BASE_URL));
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    @Override
    public String defaultModel() {
        return envOrDefault(CHAT_MODEL_ENV, DEFAULT_CHAT_MODEL);
    }

    @Override
    public String apiKeyEnvName() {
        return API_KEY_ENV;
    }

    /**
     * Ollama 本地服务默认不需要 API Key。
     */
    @Override
    public boolean apiKeyRequired() {
        return false;
    }

    /**
     * Ollama 当前可切换的 chat 模型。
     *
     * <p>用户可以通过 OLLAMA_CHAT_MODELS 配置逗号分隔的本地模型列表。
     * 没有配置时只暴露默认 chat 模型。</p>
     */
    @Override
    public List<String> switchableChatModels() {
        String configured = System.getenv(CHAT_MODELS_ENV);
        if (configured == null || configured.isBlank()) {
            return List.of(defaultModel());
        }
        LinkedHashSet<String> models = new LinkedHashSet<>();
        for (String item : configured.split(",")) {
            String model = item.strip();
            if (!model.isBlank()) {
                models.add(model);
            }
        }
        if (models.isEmpty()) {
            models.add(defaultModel());
        }
        return new ArrayList<>(models);
    }

    @Override
    public Set<ModelCapability> capabilities() {
        return Set.of(ModelCapability.CHAT_COMPLETIONS, ModelCapability.EMBEDDINGS);
    }

    /**
     * Ollama 不强制请求 OpenAI stream usage。
     */
    @Override
    public boolean streamUsageEnabled() {
        return false;
    }

    /**
     * 读取 Ollama embedding 模型名。
     */
    public String defaultEmbeddingModel() {
        return envOrDefault(EMBEDDING_MODEL_ENV, DEFAULT_EMBEDDING_MODEL);
    }

    /**
     * 根据 chat baseUrl 推导 Ollama 原生 embedding endpoint。
     */
    public static String embeddingEndpointFromBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - "/v1".length());
        }
        return normalized + "/api/embed";
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
