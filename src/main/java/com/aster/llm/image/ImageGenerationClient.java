package com.aster.llm.image;

import java.io.IOException;

/**
 * 图片生成客户端接口。
 *
 * <p>第一版只定义能力边界，具体供应商实现后续再接入。</p>
 */
public interface ImageGenerationClient {
    /**
     * 根据文本或图片输入生成图片数据。
     */
    ImageGenerationResponse generate(ImageGenerationRequest request) throws IOException;
}
