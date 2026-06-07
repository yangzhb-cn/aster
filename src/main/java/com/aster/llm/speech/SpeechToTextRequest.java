package com.aster.llm.speech;

/**
 * 语音转文字请求。
 */
public record SpeechToTextRequest(String model, byte[] audio, String mimeType, String language) {
}
