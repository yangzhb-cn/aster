package com.aster.app.rag.retrieve;

import com.aster.app.rag.RagStore;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagHit;
import com.aster.app.rag.model.RagVector;
import com.aster.llm.embedding.EmbeddingClient;
import com.aster.llm.embedding.model.EmbeddingRequest;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于向量余弦相似度的第一版召回器。
 */
public class VectorRetriever {
    private final RagStore store;
    private final EmbeddingClient embeddingClient;
    private final String embeddingModel;

    public VectorRetriever(RagStore store, EmbeddingClient embeddingClient, String embeddingModel) {
        this.store = Objects.requireNonNull(store);
        this.embeddingClient = Objects.requireNonNull(embeddingClient);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
    }

    /**
     * 对问题向量化，并从本地向量索引里召回 topK chunk。
     */
    public List<RagHit> retrieve(String kbId, String question, int topK) throws IOException {
        List<Double> queryVector = embeddingClient.embed(EmbeddingRequest.single(embeddingModel, question))
                .embeddings()
                .getFirst();
        Map<String, RagChunk> chunks = new LinkedHashMap<>();
        for (RagChunk chunk : store.loadChunks(kbId)) {
            chunks.put(chunk.chunkId(), chunk);
        }
        return store.loadVectors(kbId).stream()
                .filter(vector -> embeddingModel.equals(vector.embeddingModel()))
                .map(vector -> toHit(chunks.get(vector.chunkId()), vector, queryVector))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RagHit::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    private RagHit toHit(RagChunk chunk, RagVector vector, List<Double> queryVector) {
        if (chunk == null) {
            return null;
        }
        double score = CosineSimilarity.score(queryVector, vector.vector());
        if (!Double.isFinite(score)) {
            return null;
        }
        return new RagHit(
                chunk.kbId(),
                chunk.docId(),
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.sourceName(),
                score,
                chunk.text()
        );
    }
}
