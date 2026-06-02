package dev.agentmvp.mcp.transport;

import java.io.IOException;

/**
 * MCP JSON-RPC 传输层。
 *
 * <p>MCP 上层协议都是 JSON-RPC，但底层传输可以不同：
 * HTTP 是把 JSON-RPC 放进 HTTP POST；本地 MCP 通常是启动一个进程，
 * 通过 stdin/stdout 一行一个 JSON-RPC 消息通信。</p>
 */
public interface McpTransport extends AutoCloseable {
    /**
     * 发送一个 JSON-RPC 请求字符串，并返回对应响应字符串。
     */
    String send(String requestJson) throws IOException;

    /**
     * 默认不需要释放资源。
     */
    @Override
    default void close() throws IOException {
    }
}
