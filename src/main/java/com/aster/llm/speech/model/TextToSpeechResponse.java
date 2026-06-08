package com.aster.llm.speech.model;

/**
 * 文字转语音响应。
 */
public record TextToSpeechResponse(byte[] audio, String mimeType) {
}
