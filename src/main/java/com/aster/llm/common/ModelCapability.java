package com.aster.llm.common;

/**
 * 模型供应商支持的能力类型。
 *
 * <p>Chat、Embedding、语音、图片不要揉成一个大客户端。
 * 每种能力由自己的 Client 处理，供应商只声明自己支持哪些能力。</p>
 */
public enum ModelCapability {
    CHAT_COMPLETIONS,
    EMBEDDINGS,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH,
    IMAGE_UNDERSTANDING,
    IMAGE_GENERATION,
    RERANK
}
