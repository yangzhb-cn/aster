package dev.agentmvp.mcp;

import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.tool.model.Tool;
import dev.agentmvp.tool.ToolExecutor;
import dev.agentmvp.tool.model.ToolResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 执行来自 MCP 服务端的工具调用。
 *
 * <p>ToolRegistry 只保存工具定义，真正调用 MCP 服务端的能力在这里。
 * 这样 AgentLoop 不需要知道工具到底是本地 Java 实现，还是远端 MCP 工具。</p>
 */
public class McpToolExecutor implements ToolExecutor {
    private final Map<String, McpClient> clientsByServerId = new HashMap<>();

    /**
     * 注册一个 MCP 客户端，后续按 serverId 找到对应服务端执行工具。
     */
    public void registerClient(McpClient client) {
        clientsByServerId.put(client.serverId(), client);
    }

    /**
     * 调用具体 MCP 服务端，并始终返回一条 ToolResult 来保持 LLM 工具协议配对。
     */
    @Override
    public ToolResult execute(Tool tool, ToolCall call) {
        McpClient client = clientsByServerId.get(tool.serverId());
        if (client == null) {
            return ToolResult.error(call.id(), "No MCP client for server: " + tool.serverId());
        }

        try {
            return client.callTool(tool, call);
        } catch (Exception e) {
            // 即使 MCP 调用失败，也返回配对的工具结果，避免 LLM 工具协议断掉。
            return ToolResult.error(call.id(), e.getMessage());
        }
    }
}
