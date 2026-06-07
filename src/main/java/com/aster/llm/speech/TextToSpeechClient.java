package com.aster.llm.speech;

import java.io.IOException;

/**
 * 文字转语音客户端接口。
 */
public interface TextToSpeechClient {
    /**
     * 将文本合成为音频。
     */
    TextToSpeechResponse synthesize(TextToSpeechRequest request) throws IOException;
}
