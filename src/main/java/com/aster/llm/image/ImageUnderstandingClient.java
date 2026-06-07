package com.aster.llm.image;

import java.io.IOException;

/**
 * 图片理解客户端接口。
 */
public interface ImageUnderstandingClient {
    /**
     * 根据图片和文本问题返回模型理解结果。
     */
    ImageUnderstandingResponse understand(ImageUnderstandingRequest request) throws IOException;
}
