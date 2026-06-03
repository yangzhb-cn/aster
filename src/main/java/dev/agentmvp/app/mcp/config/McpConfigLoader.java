package dev.agentmvp.app.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.app.mcp.config.model.McpConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 读取本地 MCP 配置文件。
 *
 * <p>教学版默认读取 workspace/mcp.json。
 * 没有这个文件时返回空配置，表示当前 Agent 只使用内置工具。</p>
 */
public class McpConfigLoader {
    private final ObjectMapper objectMapper;

    public McpConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * 文件存在就解析，不存在就返回空配置。
     */
    public McpConfig loadIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return McpConfig.empty();
        }
        return objectMapper.readValue(path.toFile(), McpConfig.class);
    }
}
