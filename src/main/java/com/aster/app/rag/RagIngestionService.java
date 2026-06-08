package com.aster.app.rag;

import com.aster.app.rag.chunk.SlidingWindowChunker;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagVector;
import com.aster.app.rag.parse.DocumentParser;
import com.aster.llm.embedding.EmbeddingClient;
import com.aster.llm.embedding.model.EmbeddingRequest;
import com.aster.llm.embedding.model.EmbeddingResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RAG 文档入库服务。
 */
public class RagIngestionService {
    private static final int EMBEDDING_BATCH_SIZE = 16;

    private final JsonlRagStore store;
    private final List<DocumentParser> parsers;
    private final SlidingWindowChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final String embeddingModel;
    private final Path documentDirectory;

    public RagIngestionService(
            JsonlRagStore store,
            List<DocumentParser> parsers,
            SlidingWindowChunker chunker,
            EmbeddingClient embeddingClient,
            String embeddingModel,
            Path documentDirectory
    ) {
        this.store = Objects.requireNonNull(store);
        this.parsers = List.copyOf(Objects.requireNonNull(parsers));
        this.chunker = Objects.requireNonNull(chunker);
        this.embeddingClient = Objects.requireNonNull(embeddingClient);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
        this.documentDirectory = Objects.requireNonNull(documentDirectory);
    }

    /**
     * 保存上传文件，解析文本，滑动分块，生成 embedding 并写入本地索引。
     */
    public RagDocument ingest(String kbId, String fileName, String contentType, byte[] bytes) throws IOException {
        store.ensureDefaultKnowledgeBase();
        if (bytes == null || bytes.length == 0) {
            throw new IOException("上传文件为空");
        }
        String safeFileName = safeFileName(fileName);
        String docId = store.nextDocumentId();
        Path docDir = documentDirectory.resolve(docId);
        Files.createDirectories(docDir);
        Path sourceFile = docDir.resolve(safeFileName);
        Files.write(sourceFile, bytes);

        DocumentParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(safeFileName, contentType))
                .findFirst()
                .orElseThrow(() -> new IOException("暂不支持的文档类型: " + safeFileName));
        String parsedText = parser.parse(sourceFile);
        Path parsedFile = docDir.resolve("parsed.txt");
        Files.writeString(parsedFile, parsedText, StandardCharsets.UTF_8);

        List<RagChunk> chunks = chunker.chunk(kbId, docId, safeFileName, parsedText);
        if (chunks.isEmpty()) {
            throw new IOException("文档没有可入库文本: " + safeFileName);
        }
        List<RagVector> vectors = embedChunks(kbId, chunks);
        String now = Instant.now().toString();
        RagDocument document = new RagDocument(
                docId,
                kbId,
                safeFileName,
                contentType == null ? "" : contentType,
                sourceFile.toString(),
                parsedFile.toString(),
                chunks.size(),
                now,
                now,
                false
        );
        store.saveIngestedDocument(document, chunks, vectors);
        return document;
    }

    private List<RagVector> embedChunks(String kbId, List<RagChunk> chunks) throws IOException {
        List<RagVector> vectors = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            List<RagChunk> batch = chunks.subList(start, Math.min(chunks.size(), start + EMBEDDING_BATCH_SIZE));
            EmbeddingResponse response = embeddingClient.embed(new EmbeddingRequest(
                    embeddingModel,
                    batch.stream().map(RagChunk::text).toList()
            ));
            if (response.embeddings().size() != batch.size()) {
                throw new IOException("embedding 返回数量和 chunk 数不一致");
            }
            for (int i = 0; i < batch.size(); i++) {
                vectors.add(new RagVector(kbId, batch.get(i).chunkId(), embeddingModel, response.embeddings().get(i)));
            }
        }
        return vectors;
    }

    private String safeFileName(String fileName) {
        String cleaned = fileName == null ? "" : Path.of(fileName).getFileName().toString();
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
        return cleaned.isBlank() ? "uploaded.txt" : cleaned;
    }
}
