package com.aster.llm.speech;

import java.io.IOException;

/**
 * 语音转文字客户端接口。
 */
public interface SpeechToTextClient {
    /**
     * 将音频转写成文本。
     */
    SpeechToTextResponse transcribe(SpeechToTextRequest request) throws IOException;
}
