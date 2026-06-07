package com.aster;

import com.aster.llm.ModelCapability;
import com.aster.llm.OllamaProvider;
import com.aster.llm.embedding.EmbeddingRequest;
import com.aster.llm.embedding.EmbeddingResponse;
import com.aster.llm.embedding.OllamaEmbeddingClient;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ollama 供应商与 embedding 客户端测试。
 */
class OllamaProviderTest {
    /**
     * 验证 Ollama chat 走 OpenAI-compatible /v1/chat/completions。
     */
    @Test
    void ollamaProviderUsesOpenAiCompatibleChatEndpoint() {
        OllamaProvider definition = new OllamaProvider();
        OpenAiCompatibleProvider provider = definition.toProvider(null);

        assertEquals("ollama", provider.name());
        assertEquals("http://localhost:11434/v1/chat/completions", provider.chatCompletionsEndpoint());
        assertEquals(false, provider.apiKeyRequired());
        assertEquals(false, provider.streamUsageEnabled());
        assertEquals(true, provider.supports(ModelCapability.CHAT_COMPLETIONS));
        assertEquals(true, provider.supports(ModelCapability.EMBEDDINGS));
    }

    /**
     * 验证 Ollama embedding endpoint 会从 /v1 chat baseUrl 回到原生 /api/embed。
     */
    @Test
    void derivesOllamaEmbeddingEndpointFromChatBaseUrl() {
        assertEquals(
                "http://localhost:11434/api/embed",
                OllamaProvider.embeddingEndpointFromBaseUrl("http://localhost:11434/v1")
        );
        assertEquals(
                "http://localhost:11434/api/embed",
                OllamaProvider.embeddingEndpointFromBaseUrl("http://localhost:11434")
        );
    }

    /**
     * 验证 Ollama /api/embed 请求体和响应解析。
     */
    @Test
    void callsOllamaEmbedApi() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "model": "nomic-embed-text",
                              "embeddings": [[0.1, 0.2, 0.3]]
                            }
                            """));

            OllamaEmbeddingClient client = new OllamaEmbeddingClient(
                    new OkHttpClient(),
                    objectMapper,
                    server.url("/api/embed").toString(),
                    null
            );

            EmbeddingResponse response = client.embed(EmbeddingRequest.single("nomic-embed-text", "hello"));
            RecordedRequest recorded = server.takeRequest();
            JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());

            assertEquals("/api/embed", recorded.getPath());
            assertEquals(null, recorded.getHeader("Authorization"));
            assertEquals("nomic-embed-text", body.path("model").asText());
            assertEquals("hello", body.path("input").get(0).asText());
            assertEquals("nomic-embed-text", response.model());
            assertEquals(List.of(List.of(0.1, 0.2, 0.3)), response.embeddings());
        }
    }
}
