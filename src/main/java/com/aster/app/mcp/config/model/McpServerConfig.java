package com.aster.app.mcp.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 单个 MCP Server 的配置。
 *
 * <p>这里同时支持两种形状：
 * HTTP MCP 写 type=http + url；
 * 本地 MCP 写 command/args/env，type 可以省略，省略时会按 command 推断为 stdio。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpServerConfig(
        String id,
        String type,
        String url,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd
) {
    public McpServerConfig {
        if (args == null) {
            args = List.of();
        }
        if (env == null) {
            env = Map.of();
        }
    }

    /**
     * mcpServers 的 key 才是服务 id，Jackson 反序列化 value 时拿不到，所以这里单独回填。
     */
    public McpServerConfig withId(String id) {
        return new McpServerConfig(id, type, url, command, args, env, cwd);
    }

    /**
     * 返回最终传输类型。
     *
     * <p>兼容常见本地 MCP 配置：只写 command，不写 type。
     * 如果写了 url，则推断为 http；如果写了 command，则推断为 stdio。</p>
     */
    public String resolvedType() {
        if (type != null && !type.isBlank()) {
            return type.trim().toLowerCase();
        }
        if (url != null && !url.isBlank()) {
            return "http";
        }
        if (command != null && !command.isBlank()) {
            return "stdio";
        }
        return "";
    }
}
