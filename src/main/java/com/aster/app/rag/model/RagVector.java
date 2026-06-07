package com.aster.app.rag.model;

import java.util.List;
import java.util.Objects;

/**
 * chunk 对应的 embedding 向量。
 */
public record RagVector(
        String kbId,
        String chunkId,
        String embeddingModel,
        List<Double> vector
) {
    public RagVector {
        Objects.requireNonNull(kbId, "kbId");
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        vector = List.copyOf(Objects.requireNonNull(vector, "vector"));
    }
}
