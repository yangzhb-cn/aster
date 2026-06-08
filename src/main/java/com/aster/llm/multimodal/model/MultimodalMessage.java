package com.aster.llm.multimodal.model;

import java.util.List;

/**
 * 多模态消息。
 *
 * <p>这里的 content 固定是内容块列表，不复用 AgentLoop 的文本 Message，
 * 避免第一版图片能力污染工具调用协议和普通 Session。</p>
 */
public record MultimodalMessage(
        String role,
        List<ContentPart> content
) {
    /**
     * 创建系统约束消息。
     */
    public static MultimodalMessage system(String text) {
        return new MultimodalMessage("system", List.of(ContentPart.text(text == null ? "" : text)));
    }

    /**
     * 创建用户图文消息。
     */
    public static MultimodalMessage user(List<ContentPart> content) {
        return new MultimodalMessage("user", content == null ? List.of() : List.copyOf(content));
    }
}
