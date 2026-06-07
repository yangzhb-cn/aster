package com.aster.app.rag.model;

import com.aster.core.session.model.SessionRecord;

import java.util.List;

/**
 * RAG 问答结果。
 */
public record RagAnswer(
        SessionRecord session,
        String kbId,
        String question,
        String answer,
        String chatModel,
        String embeddingModel,
        List<RagHit> hits
) {
    public RagAnswer {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
