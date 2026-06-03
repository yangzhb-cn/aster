package dev.agentmvp.app.mcp.model;

/**
 * JSON-RPC 2.0 协议常量。
 *
 * <p>把版本号和标准错误码集中在这里，可以避免各个调用点到处手写
 * "2.0"、-32601 这类魔法值。</p>
 */
public final class JsonRpcProtocol {
    public static final String VERSION = "2.0";

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;

    private JsonRpcProtocol() {
    }
}
