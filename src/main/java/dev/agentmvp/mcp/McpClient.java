package dev.agentmvp.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.mcp.model.JsonRpcRequest;
import dev.agentmvp.mcp.model.JsonRpcResponse;
import dev.agentmvp.mcp.transport.HttpMcpTransport;
import dev.agentmvp.mcp.transport.McpTransport;
import dev.agentmvp.tool.model.Tool;
import dev.agentmvp.tool.model.ToolResult;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 最小 JSON-RPC MCP 客户端。
 *
 * <p>这个客户端只实现 MVP 需要的 MCP 方法：
 * initialize、tools/list 和 tools/call。
 * HTTP、本地 stdio 等传输细节交给 McpTransport。</p>
 */
public class McpClient implements AutoCloseable {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String serverId;
    private final ObjectMapper objectMapper;
    private final McpTransport transport;
    private final AtomicLong ids = new AtomicLong(1);

    public McpClient(String serverId, String endpoint, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this(serverId, new HttpMcpTransport(endpoint, httpClient), objectMapper);
    }

    public McpClient(String serverId, McpTransport transport, ObjectMapper objectMapper) {
        this.serverId = Objects.requireNonNull(serverId);
        this.transport = Objects.requireNonNull(transport);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public String serverId() {
        return serverId;
    }

    /**
     * 启动 MCP 生命周期。
     */
    public void initialize() throws IOException {
        // MCP 会话第一步是 initialize。Server 会返回协议版本、能力列表等。
        // MVP 只使用 tools 能力，但仍然按标准生命周期先初始化。
        send("initialize", Map.of(
                "protocolVersion", "2025-11-25",
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", "agent-small-mvp",
                        "version", "0.1.0"
                )
        ));
    }

    public List<Tool> listTools() throws IOException {
        // tools/list 是 MCP 的工具发现入口。
        // 注意这里直接转成项目自己的 Tool，而不是暴露 McpTool 给 AgentLoop。
        JsonRpcResponse response = send("tools/list", Map.of());
        List<Tool> tools = new ArrayList<>();

        for (var toolNode : response.result().path("tools")) {
            tools.add(McpToolAdapter.toTool(serverId, toolNode, objectMapper));
        }

        return tools;
    }

    public ToolResult callTool(Tool tool, ToolCall call) throws IOException {
        // LLM 的 ToolCall 使用 OpenAI-compatible 结构；
        // MCP tools/call 使用 JSON-RPC params: { name, arguments }。
        // 这里做协议形状转换，业务层不需要知道 MCP 细节。
        Map<String, Object> arguments = objectMapper.readValue(call.function().argumentsJson(), MAP_TYPE);
        JsonRpcResponse response = send("tools/call", Map.of(
                "name", tool.name(),
                "arguments", arguments
        ));

        return McpToolAdapter.toToolResult(call.id(), response.result());
    }

    private JsonRpcResponse send(String method, Object params) throws IOException {
        // MCP 的核心是 JSON-RPC 2.0：
        // { jsonrpc: "2.0", id, method, params } -> { result | error }。
        JsonRpcRequest rpcRequest = JsonRpcRequest.of(ids.getAndIncrement(), method, params);
        String json = objectMapper.writeValueAsString(rpcRequest);
        String body = transport.send(json);

        JsonRpcResponse rpcResponse = objectMapper.readValue(body, JsonRpcResponse.class);
        if (rpcResponse.hasError()) {
            // JSON-RPC error 是业务协议错误，和 HTTP 4xx/5xx 分开处理。
            throw new IOException("MCP error " + rpcResponse.error().code() + ": " + rpcResponse.error().message());
        }
        return rpcResponse;
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }
}
