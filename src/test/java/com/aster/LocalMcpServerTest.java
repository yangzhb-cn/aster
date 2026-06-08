package com.aster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.text.model.ToolCall;
import com.aster.app.mcp.McpClient;
import com.aster.app.mcp.server.LocalMcpHttpServer;
import com.aster.app.mcp.server.LocalMcpServer;
import com.aster.app.mcp.server.LocalMcpToolRegistry;
import com.aster.app.mcp.server.model.LocalMcpTool;
import com.aster.app.mcp.server.model.McpToolCallResult;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
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
                    ToolCall.function("call_local", "mcp_echo", "{\"text\":\"hello\"}")
            );

            assertEquals(1, tools.size());
            assertEquals("mcp_echo", tools.getFirst().name());
            assertEquals("echo", tools.getFirst().remoteName());
            assertFalse(result.error());
            assertEquals("call_local", result.toolCallId());
            assertEquals("local:hello", result.renderText());
        }
    }
}
