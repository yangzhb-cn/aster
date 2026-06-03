package dev.agentmvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.app.mcp.McpClient;
import dev.agentmvp.app.mcp.server.LocalMcpHttpServer;
import dev.agentmvp.app.mcp.server.LocalMcpServer;
import dev.agentmvp.app.mcp.server.LocalMcpToolRegistry;
import dev.agentmvp.app.mcp.server.model.LocalMcpTool;
import dev.agentmvp.app.mcp.server.model.McpToolCallResult;
import dev.agentmvp.core.tool.model.Tool;
import dev.agentmvp.core.tool.model.ToolResult;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 本地 MCP 服务端测试。
 *
 * <p>这里不用任何 MCP SDK，只用项目自己的 HTTP JSON-RPC 服务端，
 * 再用现有 McpClient 反向调用它，验证本地 MCP 链路能跑通。</p>
 */
class LocalMcpServerTest {
    /**
     * 验证本地 MCP 服务端可以暴露工具，并被 McpClient 正常调用。
     */
    @Test
    void exposesLocalToolThroughMcpClient() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalMcpToolRegistry registry = new LocalMcpToolRegistry();

        registry.register(new LocalMcpTool(
                "echo",
                "Echo",
                "返回输入文本",
                Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string")),
                        "required", List.of("text")
                )
        ), arguments -> McpToolCallResult.text("local:" + arguments.get("text")));

        LocalMcpServer localServer = new LocalMcpServer(
                objectMapper,
                registry,
                "local-test-tools",
                "0.1.0"
        );

        try (LocalMcpHttpServer httpServer = LocalMcpHttpServer.loopback(objectMapper, localServer, 0)) {
            httpServer.start();

            McpClient client = new McpClient(
                    "local-test-tools",
                    httpServer.endpoint(),
                    new OkHttpClient(),
                    objectMapper
            );

            client.initialize();
            List<Tool> tools = client.listTools();
            ToolResult result = client.callTool(
                    tools.getFirst(),
                    ToolCall.function("call_local", "echo", "{\"text\":\"hello\"}")
            );

            assertEquals(1, tools.size());
            assertEquals("echo", tools.getFirst().name());
            assertFalse(result.error());
            assertEquals("call_local", result.toolCallId());
            assertEquals("local:hello", result.renderText());
        }
    }
}
