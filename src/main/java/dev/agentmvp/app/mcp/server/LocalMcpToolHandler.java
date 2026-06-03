package dev.agentmvp.app.mcp.server;

import dev.agentmvp.app.mcp.server.model.McpToolCallResult;

import java.util.Map;

/**
 * 本地 MCP 工具的 Java 处理函数。
 *
 * <p>LocalMcpServer 收到 tools/call 后，会把 arguments 解析成 Map，
 * 再交给对应工具的处理器执行。</p>
 */
@FunctionalInterface
public interface LocalMcpToolHandler {
    /**
     * 执行本地工具逻辑。
     */
    McpToolCallResult handle(Map<String, Object> arguments);
}
