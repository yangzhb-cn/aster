package dev.agentmvp.app.mcp.transport;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

/**
 * MCP over HTTP 传输。
 *
 * <p>这是当前项目最早支持的传输方式：
 * 每个 JSON-RPC 请求都是一次 HTTP POST。</p>
 */
public class HttpMcpTransport implements McpTransport {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String endpoint;
    private final OkHttpClient httpClient;

    public HttpMcpTransport(String endpoint, OkHttpClient httpClient) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /**
     * 通过 HTTP POST 发送 JSON-RPC 请求。
     */
    @Override
    public String send(String requestJson) throws IOException {
        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestJson, JSON))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("MCP request failed: HTTP " + response.code() + "\n" + body);
            }
            return body;
        }
    }
}
