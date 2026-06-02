package dev.agentmvp.tool.model;

import java.util.Map;

/**
 * Agent 暴露给 LLM 的工具定义。
 *
 * <p>这里刻意只保留 OpenAI 工具结构需要的核心字段：
 * name、description、inputSchema，以及工具来源。执行细节放在 ToolExecutor。</p>
 */
public record Tool(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        ToolSource source,
        String serverId
) {
    /**
     * 创建本地 Java 工具定义。
     */
    public static Tool local(String name, String title, String description, Map<String, Object> inputSchema) {
        return new Tool(name, title, description, inputSchema, ToolSource.LOCAL, null);
    }

    /**
     * 创建来自 MCP 服务端的工具定义，并记录 serverId 方便执行时路由。
     */
    public static Tool mcp(String serverId, String name, String title, String description, Map<String, Object> inputSchema) {
        return new Tool(name, title, description, inputSchema, ToolSource.MCP, serverId);
    }
}
