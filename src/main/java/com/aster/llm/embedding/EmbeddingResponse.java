package com.aster.llm.embedding;

import java.util.List;
import java.util.Objects;

/**
 * 向量化响应。
 */
public record EmbeddingResponse(String model, List<List<Double>> embeddings) {
    public EmbeddingResponse {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(embeddings, "embeddings");
        embeddings = embeddings.stream()
                .map(vector -> List.copyOf(Objects.requireNonNull(vector, "vector")))
                .toList();
    }
}
