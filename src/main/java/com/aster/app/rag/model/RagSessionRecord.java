package com.aster.app.rag.model;

import java.util.List;

/**
 * RAG session JSONL 里的单条问答记录。
 *
 * <p>它不是普通 Agent Message，因为 assistant 记录需要保存检索命中、
 * kbId、chat 模型和 embedding 模型，便于前端回放引用来源。</p>
 */
public record RagSessionRecord(
        String type,
        String content,
        String kbId,
        String chatModel,
        String embeddingModel,
        List<RagHit> hits,
        String createdAt
) {
    public RagSessionRecord {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
