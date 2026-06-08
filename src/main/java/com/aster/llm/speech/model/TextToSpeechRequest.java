package com.aster.llm.speech.model;

/**
 * 文字转语音请求。
 */
public record TextToSpeechRequest(String model, String text, String voice) {
}
