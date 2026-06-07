package com.aster.app.rag.model;

import java.util.Objects;

/**
 * 已入库文档元信息。
 */
public record RagDocument(
        String docId,
        String kbId,
        String fileName,
        String contentType,
        String sourcePath,
        String parsedPath,
        int chunkCount,
        String createdAt,
        String updatedAt,
        boolean archived
) {
    public RagDocument {
        Objects.requireNonNull(docId, "docId");
        Objects.requireNonNull(kbId, "kbId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
