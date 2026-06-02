package dev.agentmvp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.DeepSeekModels;
import dev.agentmvp.llm.DeepSeekProvider;
import dev.agentmvp.llm.OpenAiCompatibleChatClient;
import dev.agentmvp.llm.OpenAiCompatibleProviderDefinition;
import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DeepSeek/OpenAI 兼容客户端测试。
 *
 * <p>这里用 MockWebServer 验证请求形状，不访问真实 DeepSeek 网络。</p>
 */
class DeepSeekProviderTest {
    /**
     * 验证 DeepSeek 请求使用 /chat/completions、Authorization 和 stream=true。
     */
    @Test
    void callsDeepSeekUsingOpenAiCompatibleStreamingShape() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"role":"assistant","content":"ok"}}]}
                            
                            data: [DONE]
                            
                            """));

            OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                    "deepseek",
                    server.url("/").toString(),
                    "test-key",
                    DeepSeekModels.V4_FLASH,
                    true,
                    "high"
            );

            OpenAiCompatibleChatClient.create(
                    new OkHttpClient(),
                    objectMapper,
                    provider
            ).stream(ChatRequest.streaming(
                    provider.defaultModel(),
                    List.of(Message.user("hello")),
                    List.of(),
                    null,
                    provider.thinkingEnabled(),
                    provider.reasoningEffort()
            ), chunk -> {
            });

            RecordedRequest recorded = server.takeRequest();
            JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());

            assertEquals("/chat/completions", recorded.getPath());
            assertEquals("Bearer test-key", recorded.getHeader("Authorization"));
            assertEquals("deepseek-v4-flash", body.path("model").asText());
            assertEquals(true, body.path("stream").asBoolean());
            assertEquals("enabled", body.path("thinking").path("type").asText());
            assertEquals("high", body.path("reasoning_effort").asText());
            assertEquals("user", body.path("messages").get(0).path("role").asText());
            assertEquals("hello", body.path("messages").get(0).path("content").asText());
        }
    }

    /**
     * 验证默认供应商配置会拼出 DeepSeek 的 chat completions 端点。
     */
    @Test
    void deepSeekDefaultEndpointUsesOfficialBaseUrl() {
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "deepseek",
                "https://api.deepseek.com",
                "key",
                DeepSeekModels.V4_FLASH
        );

        assertEquals("https://api.deepseek.com/chat/completions", provider.chatCompletionsEndpoint());
        assertEquals("deepseek-v4-flash", provider.defaultModel());
    }

    /**
     * 验证 DeepSeek 只是 OpenAI 兼容供应商定义的一种实现。
     */
    @Test
    void deepSeekProviderImplementsProviderDefinition() {
        OpenAiCompatibleProviderDefinition definition = new DeepSeekProvider();
        OpenAiCompatibleProvider provider = definition.toProvider("key");

        assertEquals("deepseek", provider.name());
        assertEquals("https://api.deepseek.com", provider.baseUrl());
        assertEquals("key", provider.apiKey());
        assertEquals("deepseek-v4-flash", provider.defaultModel());
        assertEquals(true, provider.thinkingEnabled());
        assertEquals("high", provider.reasoningEffort());
    }

    /**
     * 验证其他 OpenAI 兼容供应商也能走同一套定义接口。
     */
    @Test
    void customProviderCanUseSameDefinitionContract() {
        OpenAiCompatibleProviderDefinition definition = new OpenAiCompatibleProviderDefinition() {
            @Override
            public String name() {
                return "example-provider";
            }

            @Override
            public String defaultBaseUrl() {
                return "https://example.com/v1";
            }

            @Override
            public String defaultModel() {
                return "example-model";
            }

            @Override
            public String apiKeyEnvName() {
                return "EXAMPLE_API_KEY";
            }
        };

        OpenAiCompatibleProvider provider = definition.toProvider("example-key");

        assertEquals("example-provider", provider.name());
        assertEquals("https://example.com/v1/chat/completions", provider.chatCompletionsEndpoint());
        assertEquals("example-key", provider.apiKey());
        assertEquals("example-model", provider.defaultModel());
    }
}
