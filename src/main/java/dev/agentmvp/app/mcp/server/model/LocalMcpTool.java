package dev.agentmvp.app.mcp.server.model;

import java.util.Map;

/**
 * 本地 MCP 服务端暴露出去的工具描述。
 *
 * <p>这部分会出现在 tools/list 响应里，给 MCP 客户端和 LLM 看。
 * 它只描述工具“叫什么、做什么、需要什么参数”，不包含 Java 处理函数。</p>
 */
public record LocalMcpTool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema
) {
}
