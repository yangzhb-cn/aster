package com.aster.app.rag.model;

import java.util.List;

/**
 * 知识库索引文件结构。
 */
public record KnowledgeBaseIndexData(List<KnowledgeBase> knowledgeBases) {
    public KnowledgeBaseIndexData {
        knowledgeBases = knowledgeBases == null ? List.of() : List.copyOf(knowledgeBases);
    }
}
