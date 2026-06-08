package com.aster;

import com.aster.llm.multimodal.model.ContentPart;
import com.aster.llm.multimodal.model.MultimodalChatRequest;
import com.aster.llm.multimodal.model.MultimodalMessage;
import com.aster.llm.multimodal.ollama.OllamaMultimodalChatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ollama 多模态 OpenAI-compatible 客户端测试。
 */
class OllamaMultimodalChatClientTest {
    /**
     * 验证图文请求使用 content part 数组，并且可以解析流式文本增量。
     */
    @Test
    void sendsImageContentPartsAndReadsStreamingText() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"role":"assistant","content":"图"}}]}
                            
                            data: {"choices":[{"delta":{"content":"片"}}]}
                            
                            data: [DONE]
                            
                            """));

            OllamaMultimodalChatClient client = new OllamaMultimodalChatClient(
                    new OkHttpClient(),
                    objectMapper,
                    server.url("/v1/chat/completions").toString(),
                    ""
            );

            StringBuilder answer = new StringBuilder();
            client.stream(MultimodalChatRequest.streaming(
                    "llava-llama3:latest",
                    List.of(MultimodalMessage.user(List.of(
                            ContentPart.text("描述图片"),
                            ContentPart.imageData("image/png", "AAA")
                    )))
            ), new com.aster.llm.multimodal.MultimodalChatClient.StreamHandler() {
                @Override
                public void onToken(String token) {
                    answer.append(token);
                }

                @Override
                public void onDone() {
                    answer.append("|done");
                }
            });

            RecordedRequest recorded = server.takeRequest();
            JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());

            assertEquals("/v1/chat/completions", recorded.getPath());
            assertEquals("llava-llama3:latest", body.path("model").asText());
            assertEquals(true, body.path("stream").asBoolean());
            assertEquals("user", body.path("messages").get(0).path("role").asText());
            JsonNode content = body.path("messages").get(0).path("content");
            assertEquals("text", content.get(0).path("type").asText());
            assertEquals("描述图片", content.get(0).path("text").asText());
            assertEquals("image_url", content.get(1).path("type").asText());
            assertTrue(content.get(1).path("image_url").path("url").asText().startsWith("data:image/png;base64,AAA"));
            assertEquals("图片|done", answer.toString());
        }
    }
}
