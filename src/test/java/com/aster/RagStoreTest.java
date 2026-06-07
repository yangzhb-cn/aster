package com.aster;

import com.aster.app.rag.JsonlRagStore;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagVector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RAG JSONL 存储测试。
 */
class RagStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void storesDocumentsChunksAndVectors() throws Exception {
        JsonlRagStore store = new JsonlRagStore(
                new ObjectMapper(),
                tempDir.resolve("knowledge-bases"),
                tempDir.resolve("documents"),
                tempDir.resolve("chunks"),
                tempDir.resolve("indexes")
        );
        String now = Instant.now().toString();
        RagDocument document = new RagDocument(
                "doc_1",
                "default",
                "a.md",
                "text/markdown",
                "source",
                "parsed",
                1,
                now,
                now,
                false
        );
        RagChunk chunk = new RagChunk("default", "doc_1", "chunk_1", 0, "a.md", 0, 5, "hello");
        RagVector vector = new RagVector("default", "chunk_1", "embed", List.of(1.0, 0.0));

        store.saveIngestedDocument(document, List.of(chunk), List.of(vector));

        assertEquals(List.of(document), store.listDocuments("default"));
        assertEquals(List.of(chunk), store.loadChunks("default"));
        assertEquals(List.of(vector), store.loadVectors("default"));
    }
}
