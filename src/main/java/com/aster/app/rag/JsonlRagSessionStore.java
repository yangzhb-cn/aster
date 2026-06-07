package com.aster.app.rag;

import com.aster.app.rag.model.RagHit;
import com.aster.app.rag.model.RagSessionRecord;
import com.aster.core.session.SessionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RAG 问答 session JSONL 存储。
 *
 * <p>索引复用 core/session 的 SessionIndex；这里只负责写入和读取
 * RAG 专用 record，避免污染普通 Agent Message 协议。</p>
 */
public class JsonlRagSessionStore {
    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;

    public JsonlRagSessionStore(ObjectMapper objectMapper, Path sessionsDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.sessionsDirectory = Objects.requireNonNull(sessionsDirectory);
    }

    /**
     * 追加用户问题。
     */
    public synchronized void appendUser(String sessionId, String question) throws IOException {
        append(sessionId, new RagSessionRecord(
                "user",
                question == null ? "" : question,
                "",
                "",
                "",
                List.of(),
                Instant.now().toString()
        ));
    }

    /**
     * 追加模型回答和本轮引用。
     */
    public synchronized void appendAssistant(
            String sessionId,
            String answer,
            String kbId,
            String chatModel,
            String embeddingModel,
            List<RagHit> hits
    ) throws IOException {
        append(sessionId, new RagSessionRecord(
                "assistant",
                answer == null ? "" : answer,
                kbId == null ? "" : kbId,
                chatModel == null ? "" : chatModel,
                embeddingModel == null ? "" : embeddingModel,
                hits == null ? List.of() : hits,
                Instant.now().toString()
        ));
    }

    /**
     * 读取某个 RAG session 的全部问答记录。
     */
    public synchronized List<RagSessionRecord> load(String sessionId) throws IOException {
        Path file = SessionCatalog.fileFor(sessionsDirectory, sessionId);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<RagSessionRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    records.add(objectMapper.readValue(line, RagSessionRecord.class));
                }
            }
        }
        return records;
    }

    private void append(String sessionId, RagSessionRecord record) throws IOException {
        Path file = SessionCatalog.fileFor(sessionsDirectory, sessionId);
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
        )) {
            writer.write(objectMapper.writeValueAsString(record));
            writer.write('\n');
        }
    }
}
