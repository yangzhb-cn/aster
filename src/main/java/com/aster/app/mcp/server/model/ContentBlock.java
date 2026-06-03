package com.aster.app.mcp.server.model;

/**
 * MCP 工具返回的内容块。
 *
 * <p>标准 MCP 工具结果支持多种内容类型。这个 MVP 先只实现 text，
 * 因为它已经足够跑通“工具执行结果回给 LLM”的主链路。</p>
 */
public record ContentBlock(String type, String text) {
    /**
     * 创建文本内容块。
     */
    public static ContentBlock text(String text) {
        return new ContentBlock("text", text);
    }
}
