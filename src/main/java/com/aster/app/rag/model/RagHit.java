package com.aster.app.rag.model;

/**
 * 一条 RAG 检索命中。
 */
public record RagHit(
        String kbId,
        String docId,
        String chunkId,
        int chunkIndex,
        String sourceName,
        double score,
        String text
) {
}
