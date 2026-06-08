package com.aster.llm.multimodal.ollama;

import com.aster.llm.text.ollama.OllamaProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Ollama 多模态模型配置。
 *
 * <p>它复用 {@code OLLAMA_BASE_URL} 和 {@code OLLAMA_API_KEY}，
 * 但模型名独立于普通 chat 模型，避免带图请求误用纯文本模型。</p>
 */
public class OllamaMultimodalProvider {
    public static final String MODEL_ENV = "OLLAMA_MULTIMODAL_MODEL";
    public static final String MODELS_ENV = "OLLAMA_MULTIMODAL_MODELS";
    public static final String DEFAULT_MODEL = "llava-llama3:latest";

    /**
     * 多模态默认模型。
     */
    public String defaultModel() {
        return envOrDefault(MODEL_ENV, DEFAULT_MODEL);
    }

    /**
     * 可供 Web 下拉切换的多模态模型。
     */
    public List<String> switchableModels() {
        String configured = System.getenv(MODELS_ENV);
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

    /**
     * 根据可选用户输入选择模型。
     */
    public String modelOrDefault(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultModel();
        }
        String model = requestedModel.strip();
        if (!switchableModels().contains(model)) {
            throw new IllegalArgumentException("unsupported multimodal model: " + model);
        }
        return model;
    }

    /**
     * Ollama OpenAI-compatible 多模态 endpoint。
     */
    public String chatCompletionsEndpoint() {
        String baseUrl = normalizeBaseUrl(envOrDefault(OllamaProvider.BASE_URL_ENV, OllamaProvider.DEFAULT_BASE_URL));
        if (!baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl + "/v1";
        }
        return baseUrl + "/chat/completions";
    }

    /**
     * Ollama API Key；本地服务通常为空。
     */
    public String apiKey() {
        return System.getenv(OllamaProvider.API_KEY_ENV);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? OllamaProvider.DEFAULT_BASE_URL : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
