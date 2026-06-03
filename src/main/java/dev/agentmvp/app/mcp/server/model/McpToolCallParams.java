package dev.agentmvp.app.mcp.server.model;

import java.util.Map;

/**
 * tools/call 请求里的参数。
 *
 * <p>name 表示要调用哪个工具，arguments 是模型传来的 JSON 参数对象。</p>
 */
public record McpToolCallParams(
        String name,
        Map<String, Object> arguments
) {
    public McpToolCallParams {
        if (arguments == null) {
            arguments = Map.of();
        }
    }
}
