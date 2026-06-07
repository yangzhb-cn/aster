package com.aster;

import com.aster.app.rag.chunk.SlidingWindowChunker;
import com.aster.app.rag.model.RagChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RAG 滑动分块测试。
 */
class RagChunkerTest {
    @Test
    void chunksTextWithOverlap() {
        SlidingWindowChunker chunker = new SlidingWindowChunker(10, 2);

        List<RagChunk> chunks = chunker.chunk("default", "doc_1", "a.md", "0123456789abcdefghij");

        assertEquals(3, chunks.size());
        assertEquals("0123456789", chunks.get(0).text());
        assertEquals("89abcdefgh", chunks.get(1).text());
        assertEquals("ghij", chunks.get(2).text());
    }
}
