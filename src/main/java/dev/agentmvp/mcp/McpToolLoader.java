package dev.agentmvp.mcp;

import dev.agentmvp.tool.model.Tool;
import dev.agentmvp.tool.ToolRegistry;

import java.io.IOException;

/**
 * 从 MCP 服务端加载工具并注册到 ToolRegistry。
 *
 * <p>加载完成后，MCP 工具和本地工具在 AgentLoop 看起来完全一样，
 * 都是 ToolRegistry 里的 Tool。</p>
 */
public class McpToolLoader {
    private final ToolRegistry toolRegistry;
    private final McpToolExecutor mcpToolExecutor;

    public McpToolLoader(ToolRegistry toolRegistry, McpToolExecutor mcpToolExecutor) {
        this.toolRegistry = toolRegistry;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    /**
     * 初始化 MCP 服务端，注册执行器，再把服务端暴露的工具加入工具表。
     */
    public void load(McpClient client) throws IOException {
        client.initialize();
        mcpToolExecutor.registerClient(client);

        for (Tool tool : client.listTools()) {
            // 从这里开始，MCP 工具就是普通的 ToolRegistry 条目。
            toolRegistry.register(tool);
        }
    }
}
