package dev.agentmvp.core.tool.model;

/**
 * 工具返回内容块。
 *
 * <p>MCP 支持 text、image 等多种 block；这个 MVP 先把给 LLM 的结果统一收敛到 text。</p>
 */
public record ToolContent(String type, String text) {
    /**
     * 创建文本内容块。
     */
    public static ToolContent text(String text) {
        return new ToolContent("text", text);
    }
}
