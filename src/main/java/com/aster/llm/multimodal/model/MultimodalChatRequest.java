package com.aster.llm.multimodal.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 多模态 chat/completions 请求体。
 *
 * <p>第一版只服务 Ollama OpenAI-compatible 视觉模型，所以请求保持简洁：
 * 模型名、消息列表和 {@code stream=true}。</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultimodalChatRequest(
        String model,
        List<MultimodalMessage> messages,
        Boolean stream
) {
    /**
     * 构造流式多模态请求。
     */
    public static MultimodalChatRequest streaming(String model, List<MultimodalMessage> messages) {
        return new MultimodalChatRequest(model, messages, true);
    }
}
