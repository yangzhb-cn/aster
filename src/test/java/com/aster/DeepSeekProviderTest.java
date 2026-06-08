package com.aster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.deepseek.DeepSeekModels;
import com.aster.llm.text.deepseek.DeepSeekProvider;
import com.aster.llm.common.ModelCapability;
import com.aster.llm.text.openai.OpenAiCompatibleChatClient;
import com.aster.llm.text.openai.OpenAiCompatibleProviderDefinition;
import com.aster.llm.text.openai.OpenAiCompatibleStreamParser;
import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.llm.text.model.ProviderStreamEvent;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

            List<ProviderStreamEvent> events = new ArrayList<>();

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
            ), events::add);

            RecordedRequest recorded = server.takeRequest();
            JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());

            assertEquals("/chat/completions", recorded.getPath());
            assertEquals("Bearer test-key", recorded.getHeader("Authorization"));
            assertEquals("deepseek-v4-flash", body.path("model").asText());
            assertEquals(true, body.path("stream").asBoolean());
            assertEquals(true, body.path("stream_options").path("include_usage").asBoolean());
            assertEquals("enabled", body.path("thinking").path("type").asText());
            assertEquals("high", body.path("reasoning_effort").asText());
            assertEquals("user", body.path("messages").get(0).path("role").asText());
            assertEquals("hello", body.path("messages").get(0).path("content").asText());
            assertEquals("ok", ((ProviderStreamEvent.TextDelta) events.get(0)).text());
            assertEquals(ProviderStreamEvent.Done.class, events.get(1).getClass());
        }
    }

    /**
     * 验证 OpenAI-compatible parser 会把原始 choices/delta 转成统一 ProviderStreamEvent。
     */
    @Test
    void parsesOpenAiCompatibleStreamDataIntoProviderEvents() throws Exception {
        OpenAiCompatibleStreamParser parser = new OpenAiCompatibleStreamParser(new ObjectMapper());

        List<ProviderStreamEvent> events = parser.parse("""
                {
                  "choices": [
                    {
                      "delta": {
                        "role": "assistant",
                        "content": "答案",
                        "reasoning_content": "思考",
                        "tool_calls": [
                          {
                            "index": 0,
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "read",
                              "arguments": "{\\"path\\":"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 20,
                    "total_tokens": 120,
                    "prompt_cache_hit_tokens": 30,
                    "prompt_cache_miss_tokens": 70
                  }
                }
                """);

        assertEquals("答案", ((ProviderStreamEvent.TextDelta) events.get(0)).text());
        assertEquals("思考", ((ProviderStreamEvent.ReasoningDelta) events.get(1)).text());
        ProviderStreamEvent.ToolCallDeltaPart toolCall = (ProviderStreamEvent.ToolCallDeltaPart) events.get(2);
        assertEquals("call_1", toolCall.delta().id());
        assertEquals("read", toolCall.delta().function().name());
        ProviderStreamEvent.UsageDelta usage = (ProviderStreamEvent.UsageDelta) events.get(3);
        assertEquals(100, usage.usage().inputTokens());
        assertEquals(20, usage.usage().outputTokens());
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
     * 验证 DeepSeek 当前开放给用户切换的模型包含 flash 和 pro。
     */
    @Test
    void deepSeekSwitchableModelsContainFlashAndPro() {
        assertEquals(List.of("deepseek-v4-flash", "deepseek-v4-pro"), DeepSeekModels.switchableChatModels());
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
        assertEquals(true, provider.apiKeyRequired());
        assertEquals(true, provider.streamUsageEnabled());
        assertEquals(List.of("deepseek-v4-flash", "deepseek-v4-pro"), provider.switchableChatModels());
        assertEquals(true, provider.supports(ModelCapability.CHAT_COMPLETIONS));
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
