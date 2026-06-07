package com.aster.llm.image;

import java.util.List;

/**
 * 图片理解请求。
 */
public record ImageUnderstandingRequest(String model, String prompt, List<byte[]> images) {
}
