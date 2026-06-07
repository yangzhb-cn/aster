package com.aster.app.rag.model;

import java.util.Objects;

/**
 * 文档切分后的文本块。
 */
public record RagChunk(
        String kbId,
        String docId,
        String chunkId,
        int chunkIndex,
        String sourceName,
        int startOffset,
        int endOffset,
        String text
) {
    public RagChunk {
        Objects.requireNonNull(kbId, "kbId");
        Objects.requireNonNull(docId, "docId");
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(text, "text");
    }
}
