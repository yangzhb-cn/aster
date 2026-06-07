package com.aster.llm.image;

import java.util.List;

/**
 * 图片生成响应。
 */
public record ImageGenerationResponse(List<byte[]> images, String mimeType) {
}
