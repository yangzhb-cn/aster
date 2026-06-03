package com.aster.app.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.app.mcp.model.JsonRpcProtocol;
import com.aster.app.mcp.model.JsonRpcRequest;
import com.aster.app.mcp.model.JsonRpcResponse;
import com.aster.app.mcp.server.model.LocalMcpTool;
import com.aster.app.mcp.server.model.McpToolCallParams;
import com.aster.app.mcp.server.model.McpToolCallResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 本地 MCP 服务端的 JSON-RPC 分发器。
 *
 * <p>它不负责 HTTP，也不调用 LLM。它只接收 JSON-RPC 请求，
 * 识别 initialize、tools/list、tools/call，再返回 JSON-RPC 响应。</p>
 */
public class LocalMcpServer {
    public static final String PROTOCOL_VERSION = "2025-11-25";

    private final ObjectMapper objectMapper;
    private final LocalMcpToolRegistry toolRegistry;
    private final String serverName;
    private final String serverVersion;

    public LocalMcpServer(
            ObjectMapper objectMapper,
            LocalMcpToolRegistry toolRegistry,
            String serverName,
            String serverVersion
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.serverName = Objects.requireNonNull(serverName);
        this.serverVersion = Objects.requireNonNull(serverVersion);
    }

    /**
     * 处理一条 JSON-RPC 请求。
     */
    public JsonRpcResponse handle(JsonRpcRequest request) {
        if (!JsonRpcProtocol.VERSION.equals(request.jsonrpc())) {
            return error(request.id(), JsonRpcProtocol.INVALID_REQUEST, "Invalid JSON-RPC version");
        }

        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            default -> error(request.id(), JsonRpcProtocol.METHOD_NOT_FOUND, "Method not found: " + request.method());
        };
    }

    /**
     * 返回本地 MCP 服务端的协议版本和能力。
     */
    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        Map<String, Object> result = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of()
                ),
                "serverInfo", Map.of(
                        "name", serverName,
                        "version", serverVersion
                )
        );

        return success(request.id(), result);
    }

    /**
     * 返回本地注册的工具列表。
     */
    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        List<Map<String, Object>> tools = toolRegistry.listTools().stream()
                .map(this::renderTool)
                .toList();

        return success(request.id(), Map.of("tools", tools));
    }

    /**
     * 执行本地工具，并把执行结果包装成 MCP tools/call 结果。
     */
    private JsonRpcResponse handleToolsCall(JsonRpcRequest request) {
        McpToolCallParams params = objectMapper.convertValue(request.params(), McpToolCallParams.class);
        LocalMcpToolHandler handler = toolRegistry.handler(params.name());

        if (handler == null) {
            return success(request.id(), McpToolCallResult.error("未知 MCP 工具: " + params.name()));
        }

        try {
            return success(request.id(), handler.handle(params.arguments()));
        } catch (Exception e) {
            return success(request.id(), McpToolCallResult.error(e.getMessage()));
        }
    }

    /**
     * 把内部工具描述渲染成 MCP tools/list 协议对象。
     */
    private Map<String, Object> renderTool(LocalMcpTool tool) {
        return Map.of(
                "name", tool.name(),
                "title", tool.title(),
                "description", tool.description(),
                "inputSchema", tool.inputSchema()
        );
    }

    private JsonRpcResponse success(Object id, Object result) {
        return JsonRpcResponse.success(objectMapper, id, result);
    }

    private JsonRpcResponse error(Object id, int code, String message) {
        return JsonRpcResponse.error(objectMapper, id, code, message);
    }
}
