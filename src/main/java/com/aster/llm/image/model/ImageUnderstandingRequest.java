package com.aster.llm.image.model;

import java.util.List;

/**
 * 图片理解请求。
 */
public record ImageUnderstandingRequest(String model, String prompt, List<byte[]> images) {
}
