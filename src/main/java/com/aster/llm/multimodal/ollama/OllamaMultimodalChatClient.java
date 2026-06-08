package com.aster.llm.multimodal.ollama;

import com.aster.llm.multimodal.MultimodalChatClient;
import com.aster.llm.multimodal.model.MultimodalChatRequest;
import com.aster.llm.text.model.ProviderStreamEvent;
import com.aster.llm.text.openai.OpenAiCompatibleStreamParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;
import java.util.Objects;

/**
 * Ollama OpenAI-compatible 多模态客户端。
 *
 * <p>Ollama 视觉模型可以通过 {@code /v1/chat/completions} 接收
 * text/image_url 混合 content。这里复用 OpenAI-compatible SSE parser，
 * 只把可见文本增量暴露给上层。</p>
 */
public class OllamaMultimodalChatClient implements MultimodalChatClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final OpenAiCompatibleStreamParser streamParser;

    public OllamaMultimodalChatClient(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.apiKey = apiKey;
        this.streamParser = new OpenAiCompatibleStreamParser(objectMapper);
    }

    /**
     * 从 Ollama 多模态供应商配置创建客户端。
     */
    public static OllamaMultimodalChatClient fromProvider(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            OllamaMultimodalProvider provider
    ) {
        return new OllamaMultimodalChatClient(
                httpClient,
                objectMapper,
                provider.chatCompletionsEndpoint(),
                provider.apiKey()
        );
    }

    /**
     * 发送图文流式请求。
     */
    @Override
    public void stream(MultimodalChatRequest request, StreamHandler handler) throws IOException {
        String json = objectMapper.writeValueAsString(request);
        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(json, JSON))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? "" : response.peekBody(8192).string();
                throw new IOException("Ollama multimodal request failed: HTTP " + response.code() + "\n" + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("Ollama multimodal response body is empty");
            }
            readSse(response.body().source(), handler);
        }
    }

    /**
     * 解析 SSE，并把文本 token 交给上层。
     */
    private void readSse(BufferedSource source, StreamHandler handler) throws IOException {
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }
            if (line.isBlank() || !line.startsWith("data:")) {
                continue;
            }
            String data = line.substring("data:".length()).trim();
            for (ProviderStreamEvent event : streamParser.parse(data)) {
                if (event instanceof ProviderStreamEvent.TextDelta textDelta) {
                    handler.onToken(textDelta.text());
                } else if (event instanceof ProviderStreamEvent.Done) {
                    handler.onDone();
                    return;
                }
            }
        }
        handler.onDone();
    }
}
