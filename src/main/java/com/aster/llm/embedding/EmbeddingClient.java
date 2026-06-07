package com.aster.llm.embedding;

import java.io.IOException;

/**
 * 文本向量化客户端。
 *
 * <p>RAG 只依赖这个接口，不关心向量来自 Ollama、本地模型还是云厂商。</p>
 */
public interface EmbeddingClient {
    /**
     * 对输入文本进行向量化。
     */
    EmbeddingResponse embed(EmbeddingRequest request) throws IOException;
}
