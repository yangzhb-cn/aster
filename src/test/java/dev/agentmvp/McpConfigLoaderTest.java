package dev.agentmvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.app.mcp.config.McpConfigLoader;
import dev.agentmvp.app.mcp.config.model.McpConfig;
import dev.agentmvp.app.mcp.config.model.McpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 配置读取测试。
 *
 * <p>这里不安装真实 MCP，只验证 mcp.json 的配置形状能被解析。
 * 真正调用链路由 McpClientTest 和 LocalMcpServerTest 覆盖。</p>
 */
class McpConfigLoaderTest {
    private final McpConfigLoader loader = new McpConfigLoader(new ObjectMapper());

    @TempDir
    Path tempDir;

    /**
     * 没有 mcp.json 时返回空配置。
     */
    @Test
    void missingFileReturnsEmptyConfig() throws Exception {
        McpConfig config = loader.loadIfExists(tempDir.resolve("mcp.json"));

        assertTrue(config.servers().isEmpty());
    }

    /**
     * 支持常见本地 MCP 配置：只写 command，不写 type。
     */
    @Test
    void parsesStdioServerConfig() throws Exception {
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, """
                {
                  "mcpServers": {
                    "local-demo": {
                      "command": "npx",
                      "args": ["-y", "demo-mcp"],
                      "env": { "DEMO_TOKEN": "secret" },
                      "cwd": "/tmp"
                    }
                  }
                }
                """);

        List<McpServerConfig> servers = loader.loadIfExists(configFile).servers();
        McpServerConfig server = servers.getFirst();

        assertEquals(1, servers.size());
        assertEquals("local-demo", server.id());
        assertEquals("stdio", server.resolvedType());
        assertEquals("npx", server.command());
        assertEquals(List.of("-y", "demo-mcp"), server.args());
        assertEquals("secret", server.env().get("DEMO_TOKEN"));
        assertEquals("/tmp", server.cwd());
    }

    /**
     * 也支持 HTTP MCP 配置，方便接项目里已有的 LocalMcpHttpServer。
     */
    @Test
    void parsesHttpServerConfig() throws Exception {
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, """
                {
                  "mcpServers": {
                    "http-demo": {
                      "type": "http",
                      "url": "http://127.0.0.1:7777/mcp"
                    }
                  }
                }
                """);

        McpServerConfig server = loader.loadIfExists(configFile).servers().getFirst();

        assertEquals("http-demo", server.id());
        assertEquals("http", server.resolvedType());
        assertEquals("http://127.0.0.1:7777/mcp", server.url());
    }
}
