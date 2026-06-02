package dev.agentmvp.mcp.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * mcp.json 的根对象。
 *
 * <p>常见 MCP 配置都会放在 mcpServers 下：
 * key 是服务名称，value 是这个服务的启动方式。
 * 教学版把 key 回填到 McpServerConfig.id，后续工具路由就靠这个 id 找回对应客户端。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpConfig(
        @JsonProperty("mcpServers")
        Map<String, McpServerConfig> mcpServers
) {
    public McpConfig {
        if (mcpServers == null) {
            mcpServers = Map.of();
        }
    }

    /**
     * 空配置表示当前项目暂时不接任何外部 MCP。
     */
    public static McpConfig empty() {
        return new McpConfig(Map.of());
    }

    /**
     * 把 map 结构展开成列表，并把 map key 写入 serverId。
     */
    public List<McpServerConfig> servers() {
        return mcpServers.entrySet().stream()
                .map(entry -> entry.getValue().withId(entry.getKey()))
                .toList();
    }
}
