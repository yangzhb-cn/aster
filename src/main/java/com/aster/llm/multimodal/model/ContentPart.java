package com.aster.llm.multimodal.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 多模态消息里的内容块。
 *
 * <p>普通文本使用 {@code type=text}；图片使用 OpenAI-compatible
 * {@code type=image_url} 结构。这里保留标准 data URI，方便 Ollama 和后续
 * Responses/OpenAI-compatible 供应商复用。</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ContentPart(
        String type,
        String text,
        @JsonProperty("image_url") ImageUrl imageUrl
) {
    /**
     * 创建文本内容块。
     */
    public static ContentPart text(String text) {
        return new ContentPart("text", text, null);
    }

    /**
     * 创建 base64 图片内容块。
     */
    public static ContentPart imageData(String mimeType, String base64Payload) {
        String cleanMimeType = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType.strip();
        String cleanPayload = base64Payload == null ? "" : base64Payload.strip();
        return imageUrl("data:" + cleanMimeType + ";base64," + cleanPayload);
    }

    /**
     * 创建图片 URL 内容块。
     */
    public static ContentPart imageUrl(String url) {
        return new ContentPart("image_url", null, new ImageUrl(url));
    }

    /**
     * OpenAI-compatible image_url 对象。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ImageUrl(String url) {
    }
}
