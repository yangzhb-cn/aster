package com.aster.app.extension;

import com.aster.app.mcp.McpToolLoader;
import com.aster.app.mcp.config.McpClientFactory;
import com.aster.app.mcp.config.McpConfigLoader;
import com.aster.app.mcp.config.model.McpConfig;
import com.aster.app.mcp.config.model.McpServerConfig;
import com.aster.app.runtime.WorkspacePaths;

import java.io.IOException;

/**
 * MCP 工具扩展。
 *
 * <p>它复用现有 MCP 适配实现，只负责把 workspace/mcp.json 中的工具注册进 ToolRegistry。</p>
 */
public class McpToolExtension implements AsterRuntimeExtension {
    /**
     * 加载已配置的 MCP server，并把远端工具适配成普通 Tool。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) throws IOException {
        McpConfig mcpConfig = new McpConfigLoader(context.objectMapper()).loadIfExists(WorkspacePaths.MCP_CONFIG);
        McpClientFactory clientFactory = new McpClientFactory(context.httpClient(), context.objectMapper());
        McpToolLoader toolLoader = new McpToolLoader(context.toolRegistry(), context.mcpToolExecutor());

        for (McpServerConfig serverConfig : mcpConfig.servers()) {
            try {
                int toolCount = toolLoader.load(clientFactory.create(serverConfig));
                context.mcpToolExecutor().recordLoaded(serverConfig.id(), toolCount);
            } catch (IOException | RuntimeException e) {
                // 单个 MCP server 加载失败不拖垮主 runtime，失败状态交给 Web/TUI 展示。
                context.mcpToolExecutor().recordFailed(serverConfig.id(), e.getMessage());
            }
        }
    }
}
