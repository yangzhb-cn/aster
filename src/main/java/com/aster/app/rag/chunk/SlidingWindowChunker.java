package com.aster.app.rag.chunk;

import com.aster.app.rag.model.RagChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 字符级滑动窗口分块器。
 *
 * <p>第一版不做复杂语义分块。先用固定窗口和重叠区保证 PDF/Markdown
 * 都能稳定入库；后续再升级为标题/段落感知分块。</p>
 */
public class SlidingWindowChunker {
    private final int chunkSizeChars;
    private final int overlapChars;

    public SlidingWindowChunker(int chunkSizeChars, int overlapChars) {
        if (chunkSizeChars <= 0) {
            throw new IllegalArgumentException("chunkSizeChars must be positive");
        }
        if (overlapChars < 0 || overlapChars >= chunkSizeChars) {
            throw new IllegalArgumentException("overlapChars must be >= 0 and < chunkSizeChars");
        }
        this.chunkSizeChars = chunkSizeChars;
        this.overlapChars = overlapChars;
    }

    /**
     * 将文本切成带重叠的 chunk。
     */
    public List<RagChunk> chunk(String kbId, String docId, String sourceName, String text) {
        Objects.requireNonNull(kbId);
        Objects.requireNonNull(docId);
        Objects.requireNonNull(sourceName);
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<RagChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSizeChars);
            String chunkText = normalized.substring(start, end).strip();
            if (!chunkText.isBlank()) {
                chunks.add(new RagChunk(
                        kbId,
                        docId,
                        docId + "_chunk_" + index,
                        index,
                        sourceName,
                        start,
                        end,
                        chunkText
                ));
                index++;
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlapChars);
        }
        return chunks;
    }

    private String normalize(String text) {
        return text == null
                ? ""
                : text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
