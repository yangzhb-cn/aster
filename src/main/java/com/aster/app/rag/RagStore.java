package com.aster.app.rag;

import com.aster.app.rag.model.KnowledgeBase;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagVector;

import java.io.IOException;
import java.util.List;

/**
 * RAG 知识库本地存储接口。
 */
public interface RagStore {
    /**
     * 确保默认知识库存在。
     */
    KnowledgeBase ensureDefaultKnowledgeBase() throws IOException;

    /**
     * 列出未归档知识库。
     */
    List<KnowledgeBase> listKnowledgeBases() throws IOException;

    /**
     * 创建知识库。
     */
    KnowledgeBase createKnowledgeBase(String name) throws IOException;

    /**
     * 保存文档、chunk 和向量。
     */
    void saveIngestedDocument(RagDocument document, List<RagChunk> chunks, List<RagVector> vectors) throws IOException;

    /**
     * 列出某个知识库的文档。
     */
    List<RagDocument> listDocuments(String kbId) throws IOException;

    /**
     * 读取某个知识库所有 chunk。
     */
    List<RagChunk> loadChunks(String kbId) throws IOException;

    /**
     * 读取某个知识库所有向量。
     */
    List<RagVector> loadVectors(String kbId) throws IOException;
}
