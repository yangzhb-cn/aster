package dev.agentmvp.app.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.app.mcp.McpClient;
import dev.agentmvp.app.mcp.config.model.McpServerConfig;
import dev.agentmvp.app.mcp.transport.StdioMcpTransport;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.Objects;

/**
 * 根据配置创建 MCP 客户端。
 *
 * <p>McpClient 只关心 JSON-RPC 方法，传输方式由这里决定。
 * 所以以后新增 SSE、WebSocket 等传输时，也只扩展这个工厂，不改 AgentLoop。</p>
 */
public class McpClientFactory {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public McpClientFactory(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * 按配置创建一个 MCP 客户端。
     */
    public McpClient create(McpServerConfig config) throws IOException {
        String serverId = requireText(config.id(), "MCP server id is required");
        String type = config.resolvedType();

        return switch (type) {
            case "http" -> createHttpClient(serverId, config);
            case "stdio", "local" -> createStdioClient(serverId, config);
            default -> throw new IOException("Unsupported MCP transport type for server " + serverId + ": " + type);
        };
    }

    /**
     * 创建 HTTP MCP 客户端。
     */
    private McpClient createHttpClient(String serverId, McpServerConfig config) throws IOException {
        String url = requireText(config.url(), "MCP http server " + serverId + " requires url");
        return new McpClient(serverId, url, httpClient, objectMapper);
    }

    /**
     * 创建本地 stdio MCP 客户端，并启动对应子进程。
     */
    private McpClient createStdioClient(String serverId, McpServerConfig config) throws IOException {
        String command = requireText(config.command(), "MCP stdio server " + serverId + " requires command");
        return new McpClient(
                serverId,
                new StdioMcpTransport(objectMapper, command, config.args(), config.env(), config.cwd()),
                objectMapper
        );
    }

    private String requireText(String value, String message) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(message);
        }
        return value;
    }
}
