package dev.agentmvp.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.ChatStreamChunk;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;
import java.util.Objects;

/**
 * 基于 OkHttp 的 OpenAI 兼容 SSE chat completions 客户端。
 *
 * <p>SSE 本质上是一条较长时间保持打开的 HTTP 响应。
 * 有效数据行通常以 {@code data:} 开头；这里按“一行 data 对应一个 JSON 片段”
 * 解析，遇到 {@code [DONE]} 表示流结束。</p>
 */
public class OkHttpStreamingChatClient implements StreamingChatClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;

    public OkHttpStreamingChatClient(OkHttpClient httpClient, ObjectMapper objectMapper, String endpoint, String apiKey) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.apiKey = apiKey;
    }

    /**
     * 向模型供应商发送流式请求。
     *
     * <p>请求体里必须已经带上 {@code stream=true}；
     * 这个类只负责 HTTP 调用和 SSE 解析。</p>
     */
    @Override
    public void stream(ChatRequest request, StreamHandler handler) throws IOException {
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
                throw new IOException("LLM stream request failed: HTTP " + response.code() + "\n" + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("LLM stream response body is empty");
            }

            readSse(response.body().source(), handler);
        }
    }

    /**
     * 解析 OpenAI 兼容格式的 SSE 行。
     */
    private void readSse(BufferedSource source, StreamHandler handler) throws IOException {
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }

            // OpenAI 兼容供应商通常每条 data 行返回一个 JSON 对象。
            // 空行只是 SSE 事件分隔符，MVP 里可以忽略。
            if (line.isBlank() || !line.startsWith("data:")) {
                continue;
            }

            String data = line.substring("data:".length()).trim();
            if ("[DONE]".equals(data)) {
                handler.onDone();
                return;
            }

            handler.onChunk(objectMapper.readValue(data, ChatStreamChunk.class));
        }
    }
}
