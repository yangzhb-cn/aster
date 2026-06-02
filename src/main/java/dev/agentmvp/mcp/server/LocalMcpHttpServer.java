package dev.agentmvp.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.agentmvp.mcp.model.JsonRpcProtocol;
import dev.agentmvp.mcp.model.JsonRpcRequest;
import dev.agentmvp.mcp.model.JsonRpcResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 本地 HTTP 版 MCP 服务端。
 *
 * <p>它只监听一个 /mcp 入口，接收 JSON-RPC 请求并返回 JSON-RPC 响应。
 * 本地开发时建议绑定 127.0.0.1，避免把工具服务暴露到局域网。</p>
 */
public class LocalMcpHttpServer implements AutoCloseable {
    private static final String MCP_PATH = "/mcp";

    private final ObjectMapper objectMapper;
    private final LocalMcpServer localMcpServer;
    private final InetSocketAddress address;
    private HttpServer httpServer;

    public LocalMcpHttpServer(ObjectMapper objectMapper, LocalMcpServer localMcpServer, InetSocketAddress address) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.localMcpServer = Objects.requireNonNull(localMcpServer);
        this.address = Objects.requireNonNull(address);
    }

    /**
     * 创建只监听本机回环地址的 MCP HTTP 服务端。
     */
    public static LocalMcpHttpServer loopback(ObjectMapper objectMapper, LocalMcpServer localMcpServer, int port) {
        return new LocalMcpHttpServer(
                objectMapper,
                localMcpServer,
                new InetSocketAddress("127.0.0.1", port)
        );
    }

    /**
     * 启动 HTTP 服务。
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(address, 0);
        httpServer.createContext(MCP_PATH, this::handle);
        httpServer.start();
    }

    /**
     * 返回当前 /mcp 访问地址。
     */
    public String endpoint() {
        if (httpServer == null) {
            throw new IllegalStateException("Local MCP HTTP server has not started");
        }
        return "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort() + MCP_PATH;
    }

    /**
     * 处理一次 HTTP 请求。
     */
    private void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeText(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            JsonRpcRequest request = objectMapper.readValue(exchange.getRequestBody(), JsonRpcRequest.class);
            JsonRpcResponse response = localMcpServer.handle(request);
            writeJson(exchange, 200, response);
        } catch (Exception e) {
            JsonRpcResponse response = JsonRpcResponse.error(
                    objectMapper,
                    null,
                    JsonRpcProtocol.PARSE_ERROR,
                    "Parse error: " + e.getMessage()
            );
            writeJson(exchange, 400, response);
        }
    }

    /**
     * 写出 JSON 响应。
     */
    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        writeBytes(exchange, status, "application/json; charset=utf-8", bytes);
    }

    /**
     * 写出普通文本响应。
     */
    private void writeText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writeBytes(exchange, status, "text/plain; charset=utf-8", bytes);
    }

    /**
     * 写出 HTTP 响应的公共部分。
     */
    private void writeBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 停止 HTTP 服务。
     */
    @Override
    public void close() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }
}
