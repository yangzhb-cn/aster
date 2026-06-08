package com.aster.llm.embedding.model;

import java.util.List;
import java.util.Objects;

/**
 * 向量化请求。
 */
public record EmbeddingRequest(String model, List<String> inputs) {
    public EmbeddingRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(inputs, "inputs");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        inputs = inputs.stream()
                .map(input -> Objects.requireNonNull(input, "input"))
                .toList();
    }

    /**
     * 创建单条文本的向量化请求。
     */
    public static EmbeddingRequest single(String model, String input) {
        return new EmbeddingRequest(model, List.of(input));
    }
}
