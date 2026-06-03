package com.aster.app.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON-RPC 2.0 响应对象。
 *
 * <p>正常返回看 result，失败返回看 error。McpClient 会在这里判断是否失败。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
        String jsonrpc,
        JsonNode id,
        JsonNode result,
        JsonRpcError error
) {
    /**
     * 创建 JSON-RPC 成功响应。
     *
     * <p>调用方传普通 Java 对象即可，这里统一转成 JsonNode，
     * 避免业务代码到处写 objectMapper.valueToTree。</p>
     */
    public static JsonRpcResponse success(ObjectMapper objectMapper, Object id, Object result) {
        return new JsonRpcResponse(
                JsonRpcProtocol.VERSION,
                toJsonNode(objectMapper, id),
                objectMapper.valueToTree(result),
                null
        );
    }

    /**
     * 创建 JSON-RPC 错误响应。
     */
    public static JsonRpcResponse error(ObjectMapper objectMapper, Object id, int code, String message) {
        return new JsonRpcResponse(
                JsonRpcProtocol.VERSION,
                toJsonNode(objectMapper, id),
                null,
                new JsonRpcError(code, message, null)
        );
    }

    /**
     * JSON-RPC 语义下，只要 error 不为空就代表方法调用失败。
     */
    public boolean hasError() {
        return error != null;
    }

    private static JsonNode toJsonNode(ObjectMapper objectMapper, Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }
}
