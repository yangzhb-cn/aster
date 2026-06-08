package com.aster.llm.embedding.ollama;

import com.aster.llm.embedding.EmbeddingClient;
import com.aster.llm.embedding.model.EmbeddingRequest;
import com.aster.llm.embedding.model.EmbeddingResponse;
import com.aster.llm.text.ollama.OllamaProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ollama 原生 embedding 客户端。
 *
 * <p>Ollama 的 embedding API 是 {@code /api/embed}，不是 OpenAI-compatible
 * {@code /v1/chat/completions} 的一部分，因此单独实现。</p>
 */
public class OllamaEmbeddingClient implements EmbeddingClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;

    public OllamaEmbeddingClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.apiKey = apiKey;
    }

    /**
     * 根据 Ollama baseUrl 创建 embedding 客户端。
     */
    public static OllamaEmbeddingClient fromBaseUrl(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey
    ) {
        return new OllamaEmbeddingClient(
                httpClient,
                objectMapper,
                OllamaProvider.embeddingEndpointFromBaseUrl(baseUrl),
                apiKey
        );
    }

    /**
     * 调用 Ollama /api/embed 并解析向量列表。
     */
    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("input", request.inputs());
        String json = objectMapper.writeValueAsString(body);

        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(json, JSON))
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? "" : response.peekBody(8192).string();
                throw new IOException("Ollama embedding request failed: HTTP " + response.code() + "\n" + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("Ollama embedding response body is empty");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            return new EmbeddingResponse(
                    root.path("model").asText(request.model()),
                    parseEmbeddings(root.path("embeddings"))
            );
        }
    }

    private List<List<Double>> parseEmbeddings(JsonNode embeddingsNode) throws IOException {
        if (!embeddingsNode.isArray()) {
            throw new IOException("Ollama embedding response missing embeddings array");
        }
        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode vectorNode : embeddingsNode) {
            if (!vectorNode.isArray()) {
                throw new IOException("Ollama embedding vector must be an array");
            }
            List<Double> vector = new ArrayList<>();
            for (JsonNode valueNode : vectorNode) {
                vector.add(valueNode.asDouble());
            }
            embeddings.add(vector);
        }
        return embeddings;
    }
}
