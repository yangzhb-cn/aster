package com.aster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.model.ToolCall;
import com.aster.app.mcp.McpClient;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * MCP HTTP JSON-RPC 客户端测试。
 *
 * <p>测试覆盖 initialize、tools/list、tools/call 三步最小链路。</p>
 */
class McpClientTest {
    /**
     * 验证能从 MCP 服务端加载工具，并把工具调用结果转回 ToolResult。
     */
    @Test
    void loadsAndCallsMcpTool() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": {
                        "protocolVersion": "2025-11-25",
                        "capabilities": { "tools": {} },
                        "serverInfo": { "name": "mock", "version": "1.0.0" }
                      }
                    }
                    """));
            server.enqueue(json("""
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "tools": [{
                          "name": "remote_echo",
                          "title": "Remote Echo",
                          "description": "Echo from MCP",
                          "inputSchema": {
                            "type": "object",
                            "properties": { "text": { "type": "string" } },
                            "required": ["text"]
                          }
                        }]
                      }
                    }
                    """));
            server.enqueue(json("""
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "result": {
                        "isError": false,
                        "content": [{ "type": "text", "text": "remote:hello" }]
                      }
                    }
                    """));

            McpClient client = new McpClient(
                    "mock-server",
                    server.url("/mcp").toString(),
                    new OkHttpClient(),
                    new ObjectMapper()
            );

            client.initialize();
            List<Tool> tools = client.listTools();
            ToolResult result = client.callTool(
                    tools.getFirst(),
                    ToolCall.function("call_mcp", "remote_echo", "{\"text\":\"hello\"}")
            );

            assertEquals(1, tools.size());
            assertEquals("remote_echo", tools.getFirst().name());
            assertFalse(result.error());
            assertEquals("call_mcp", result.toolCallId());
            assertEquals("remote:hello", result.renderText());
        }
    }

    /**
     * 构造 JSON-RPC 模拟响应。
     */
    private static MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
