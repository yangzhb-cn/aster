package com.aster.app.rag.model;

import java.util.Objects;

/**
 * 知识库元信息。
 */
public record KnowledgeBase(
        String kbId,
        String name,
        String createdAt,
        String updatedAt,
        boolean archived
) {
    public KnowledgeBase {
        Objects.requireNonNull(kbId, "kbId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
