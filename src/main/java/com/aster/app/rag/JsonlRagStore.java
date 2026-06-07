package com.aster.app.rag;

import com.aster.app.rag.model.KnowledgeBase;
import com.aster.app.rag.model.KnowledgeBaseIndexData;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagVector;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 基于 JSON/JSONL 的 RAG 本地存储。
 *
 * <p>第一版不引入数据库。知识库元信息用 JSON，chunks 和 vectors 用 JSONL，
 * 这样和现有 Session/Todo/Room 的本地文件风格保持一致。</p>
 */
public class JsonlRagStore implements RagStore {
    public static final String DEFAULT_KB_ID = "default";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,96}");
    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final Path kbDirectory;
    private final Path documentDirectory;
    private final Path chunkDirectory;
    private final Path vectorDirectory;
    private final Path kbIndexFile;

    public JsonlRagStore(
            ObjectMapper objectMapper,
            Path kbDirectory,
            Path documentDirectory,
            Path chunkDirectory,
            Path vectorDirectory
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.kbDirectory = Objects.requireNonNull(kbDirectory);
        this.documentDirectory = Objects.requireNonNull(documentDirectory);
        this.chunkDirectory = Objects.requireNonNull(chunkDirectory);
        this.vectorDirectory = Objects.requireNonNull(vectorDirectory);
        this.kbIndexFile = kbDirectory.resolve("index.json");
    }

    /**
     * 创建所有 RAG 数据目录。
     */
    public void ensureDirectories() throws IOException {
        Files.createDirectories(kbDirectory);
        Files.createDirectories(documentDirectory);
        Files.createDirectories(chunkDirectory);
        Files.createDirectories(vectorDirectory);
    }

    @Override
    public synchronized KnowledgeBase ensureDefaultKnowledgeBase() throws IOException {
        ensureDirectories();
        KnowledgeBaseIndexData data = loadKnowledgeBaseIndex();
        for (KnowledgeBase kb : data.knowledgeBases()) {
            if (DEFAULT_KB_ID.equals(kb.kbId())) {
                return kb;
            }
        }
        String now = Instant.now().toString();
        KnowledgeBase kb = new KnowledgeBase(DEFAULT_KB_ID, "默认知识库", now, now, false);
        List<KnowledgeBase> updated = new ArrayList<>(data.knowledgeBases());
        updated.add(kb);
        saveKnowledgeBaseIndex(new KnowledgeBaseIndexData(updated));
        return kb;
    }

    @Override
    public synchronized List<KnowledgeBase> listKnowledgeBases() throws IOException {
        ensureDefaultKnowledgeBase();
        return loadKnowledgeBaseIndex().knowledgeBases().stream()
                .filter(kb -> !kb.archived())
                .sorted(Comparator.comparing(KnowledgeBase::updatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized KnowledgeBase createKnowledgeBase(String name) throws IOException {
        ensureDirectories();
        KnowledgeBaseIndexData data = loadKnowledgeBaseIndex();
        String now = Instant.now().toString();
        String kbId = generateId("kb_", data.knowledgeBases().stream()
                .map(KnowledgeBase::kbId)
                .toList());
        KnowledgeBase kb = new KnowledgeBase(kbId, cleanName(name, "新知识库"), now, now, false);
        List<KnowledgeBase> updated = new ArrayList<>(data.knowledgeBases());
        updated.add(kb);
        saveKnowledgeBaseIndex(new KnowledgeBaseIndexData(updated));
        return kb;
    }

    @Override
    public synchronized void saveIngestedDocument(
            RagDocument document,
            List<RagChunk> chunks,
            List<RagVector> vectors
    ) throws IOException {
        requireId(document.kbId());
        requireId(document.docId());
        ensureDirectories();
        Files.createDirectories(documentDirectory.resolve(document.docId()));
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(documentDirectory.resolve(document.docId()).resolve("metadata.json").toFile(), document);
        appendJsonl(chunksFile(document.kbId()), chunks);
        appendJsonl(vectorsFile(document.kbId()), vectors);
    }

    @Override
    public synchronized List<RagDocument> listDocuments(String kbId) throws IOException {
        requireId(kbId);
        ensureDirectories();
        if (!Files.exists(documentDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(documentDirectory)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("metadata.json"))
                    .filter(Files::isRegularFile)
                    .map(this::readDocumentQuietly)
                    .filter(document -> document != null && kbId.equals(document.kbId()) && !document.archived())
                    .sorted(Comparator.comparing(RagDocument::updatedAt).reversed())
                    .toList();
        }
    }

    @Override
    public synchronized List<RagChunk> loadChunks(String kbId) throws IOException {
        requireId(kbId);
        return readJsonl(chunksFile(kbId), RagChunk.class);
    }

    @Override
    public synchronized List<RagVector> loadVectors(String kbId) throws IOException {
        requireId(kbId);
        return readJsonl(vectorsFile(kbId), RagVector.class);
    }

    /**
     * 创建一个新的文档 id。
     */
    public synchronized String nextDocumentId() throws IOException {
        ensureDirectories();
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        if (Files.exists(documentDirectory)) {
            try (var stream = Files.list(documentDirectory)) {
                stream.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .forEach(existing::add);
            }
        }
        return generateId("doc_", new ArrayList<>(existing));
    }

    private RagDocument readDocumentQuietly(Path metadata) {
        try {
            return objectMapper.readValue(metadata.toFile(), RagDocument.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private KnowledgeBaseIndexData loadKnowledgeBaseIndex() throws IOException {
        ensureDirectories();
        if (!Files.exists(kbIndexFile)) {
            return new KnowledgeBaseIndexData(List.of());
        }
        return objectMapper.readValue(Files.readString(kbIndexFile, StandardCharsets.UTF_8), KnowledgeBaseIndexData.class);
    }

    private void saveKnowledgeBaseIndex(KnowledgeBaseIndexData data) throws IOException {
        ensureDirectories();
        Files.writeString(
                kbIndexFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }

    private <T> void appendJsonl(Path file, List<T> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return;
        }
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
        )) {
            for (T record : records) {
                writer.write(objectMapper.writeValueAsString(record));
                writer.write('\n');
            }
        }
    }

    private <T> List<T> readJsonl(Path file, Class<T> type) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<T> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    records.add(objectMapper.readValue(line, type));
                }
            }
        }
        return records;
    }

    private Path chunksFile(String kbId) {
        requireId(kbId);
        return chunkDirectory.resolve(kbId + ".jsonl");
    }

    private Path vectorsFile(String kbId) {
        requireId(kbId);
        return vectorDirectory.resolve(kbId + ".vectors.jsonl");
    }

    private String generateId(String prefix, List<String> existing) {
        String id = prefix + ID_TIME_FORMATTER.format(Instant.now()) + "_" + randomSuffix();
        while (existing.contains(id)) {
            id = prefix + ID_TIME_FORMATTER.format(Instant.now()) + "_" + randomSuffix();
        }
        return id;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String cleanName(String name, String fallback) {
        String cleaned = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static void requireId(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("RAG id 只能包含字母、数字、点、下划线、短横线，长度 1 到 96");
        }
    }
}
