package dev.agentmvp.app.mcp.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * tools/call 的返回结果。
 *
 * <p>工具执行失败也建议作为工具结果返回，也就是 isError=true。
 * 这样 JSON-RPC 请求本身仍然成功，客户端还能把失败内容写回 LLM。</p>
 */
public record McpToolCallResult(
        @JsonProperty("isError") boolean isError,
        List<ContentBlock> content
) {
    /**
     * 创建成功的文本工具结果。
     */
    public static McpToolCallResult text(String text) {
        return new McpToolCallResult(false, List.of(ContentBlock.text(text)));
    }

    /**
     * 创建失败的文本工具结果。
     */
    public static McpToolCallResult error(String message) {
        return new McpToolCallResult(true, List.of(ContentBlock.text(message)));
    }
}
