package com.aster.app.mcp.model;

/**
 * JSON-RPC 2.0 请求对象。
 *
 * <p>MCP 的 initialize、tools/list、tools/call 都是这种结构：
 * jsonrpc 固定为 2.0，method 表示动作，params 放参数。</p>
 */
public record JsonRpcRequest(
        String jsonrpc,
        Object id,
        String method,
        Object params
) {
    /**
     * 创建标准 JSON-RPC 2.0 请求，避免调用处重复传 jsonrpc 字段。
     */
    public static JsonRpcRequest of(Object id, String method, Object params) {
        return new JsonRpcRequest(JsonRpcProtocol.VERSION, id, method, params);
    }
}
