package com.aster.llm.image;

import java.util.List;

/**
 * 图片生成请求。
 */
public record ImageGenerationRequest(String model, String prompt, List<byte[]> inputImages) {
}
