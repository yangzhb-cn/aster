package com.aster.app.mcp;

import com.aster.core.tool.model.Tool;
import com.aster.core.tool.ToolRegistry;

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
    public int load(McpClient client) throws IOException {
        try {
            client.initialize();
            java.util.List<Tool> tools = client.listTools();

            mcpToolExecutor.registerClient(client);
            for (Tool tool : tools) {
                // 从这里开始，MCP 工具就是普通的 ToolRegistry 条目。
                toolRegistry.register(tool);
            }
            return tools.size();
        } catch (IOException | RuntimeException e) {
            // 加载失败时客户端还没进入 McpToolExecutor。
            // 如果这是 stdio MCP，这里必须主动关闭刚启动的子进程。
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭失败不覆盖真正的加载错误。
            }
            throw e;
        }
    }
}
