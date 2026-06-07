package com.aster;

import com.aster.app.rag.JsonlRagStore;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagHit;
import com.aster.app.rag.model.RagVector;
import com.aster.app.rag.retrieve.VectorRetriever;
import com.aster.llm.embedding.EmbeddingClient;
import com.aster.llm.embedding.EmbeddingRequest;
import com.aster.llm.embedding.EmbeddingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RAG 向量召回测试。
 */
class RagRetrieverTest {
    @TempDir
    Path tempDir;

    @Test
    void retrievesTopChunksByCosineSimilarity() throws Exception {
        JsonlRagStore store = new JsonlRagStore(
                new ObjectMapper(),
                tempDir.resolve("knowledge-bases"),
                tempDir.resolve("documents"),
                tempDir.resolve("chunks"),
                tempDir.resolve("indexes")
        );
        String now = Instant.now().toString();
        store.saveIngestedDocument(
                new RagDocument("doc_1", "default", "a.md", "", "", "", 2, now, now, false),
                List.of(
                        new RagChunk("default", "doc_1", "chunk_1", 0, "a.md", 0, 3, "aaa"),
                        new RagChunk("default", "doc_1", "chunk_2", 1, "a.md", 3, 6, "bbb")
                ),
                List.of(
                        new RagVector("default", "chunk_1", "embed", List.of(1.0, 0.0)),
                        new RagVector("default", "chunk_2", "embed", List.of(0.0, 1.0))
                )
        );
        EmbeddingClient embeddingClient = (EmbeddingRequest request) ->
                new EmbeddingResponse(request.model(), List.of(List.of(0.9, 0.1)));

        List<RagHit> hits = new VectorRetriever(store, embeddingClient, "embed")
                .retrieve("default", "query", 1);

        assertEquals(1, hits.size());
        assertEquals("chunk_1", hits.getFirst().chunkId());
    }
}
