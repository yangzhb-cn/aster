package dev.agentmvp.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 错误对象。
 *
 * <p>MCP 建在 JSON-RPC 上，错误不会直接表现为 HTTP 非 2xx，
 * 而是出现在 response.error 字段里。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(
        int code,
        String message,
        JsonNode data
) {
}
