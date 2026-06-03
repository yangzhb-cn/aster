package com.aster.core.session;

import com.aster.core.session.model.SessionIndexData;
import com.aster.core.session.model.SessionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Session 元信息索引。
 *
 * <p>JSONL 文件名是稳定 sessionId；index.json 保存 displayName、时间和归档状态。
 * 删除会话只把 archived 置为 true，不删除 JSONL 审计文件。</p>
 */
public class SessionIndex {
    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;
    private final Path indexFile;

    public SessionIndex(ObjectMapper objectMapper, Path sessionsDirectory) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.sessionsDirectory = Objects.requireNonNull(sessionsDirectory);
        this.indexFile = sessionsDirectory.resolve("index.json");
    }

    /**
     * 列出未归档 session，按更新时间倒序。
     */
    public synchronized List<SessionRecord> listActive() throws IOException {
        return load().sessions().stream()
                .filter(record -> !record.archived())
                .sorted(Comparator.comparing(SessionRecord::updatedAt).reversed())
                .toList();
    }

    /**
     * 确保 session 在索引中存在；旧的 name.jsonl 会被懒加载成 displayName。
     */
    public synchronized SessionRecord ensure(String sessionId, String displayName) throws IOException {
        SessionCatalog.requireValidName(sessionId);
        SessionIndexData data = load();
        Optional<SessionRecord> existing = find(data, sessionId);
        if (existing.isPresent()) {
            SessionRecord record = existing.get();
            if (!record.archived()) {
                return record;
            }
            return update(sessionId, value -> value.restored(now()));
        }

        String now = now();
        SessionRecord record = new SessionRecord(
                sessionId,
                cleanDisplayName(displayName, sessionId),
                now,
                now,
                false
        );
        List<SessionRecord> records = new ArrayList<>(data.sessions());
        records.add(record);
        save(new SessionIndexData(records));
        return record;
    }

    /**
     * 创建一个新 session 记录，并返回生成的 sessionId。
     */
    public synchronized SessionRecord create(String displayName) throws IOException {
        SessionIndexData data = load();
        String id = generateId(data);
        String now = now();
        SessionRecord record = new SessionRecord(
                id,
                cleanDisplayName(displayName, defaultDisplayName(now)),
                now,
                now,
                false
        );
        List<SessionRecord> records = new ArrayList<>(data.sessions());
        records.add(record);
        save(new SessionIndexData(records));
        return record;
    }

    /**
     * 更新展示名。
     */
    public synchronized SessionRecord rename(String sessionId, String displayName) throws IOException {
        return update(sessionId, record -> record.withDisplayName(cleanDisplayName(displayName, record.displayName()), now()));
    }

    /**
     * 标记 session 已归档。
     */
    public synchronized SessionRecord archive(String sessionId) throws IOException {
        return update(sessionId, record -> record.archived(now()));
    }

    /**
     * 刷新 session 更新时间。
     */
    public synchronized void touch(String sessionId) throws IOException {
        update(sessionId, record -> record.touched(now()));
    }

    /**
     * 根据 id 读取索引记录。
     */
    public synchronized Optional<SessionRecord> get(String sessionId) throws IOException {
        SessionCatalog.requireValidName(sessionId);
        return find(load(), sessionId);
    }

    private SessionRecord update(String sessionId, Updater updater) throws IOException {
        SessionCatalog.requireValidName(sessionId);
        SessionIndexData data = load();
        List<SessionRecord> records = new ArrayList<>();
        SessionRecord updated = null;
        for (SessionRecord record : data.sessions()) {
            if (record.id().equals(sessionId)) {
                updated = updater.update(record);
                records.add(updated);
            } else {
                records.add(record);
            }
        }
        if (updated == null) {
            throw new IOException("session not found: " + sessionId);
        }
        save(new SessionIndexData(records));
        return updated;
    }

    private Optional<SessionRecord> find(SessionIndexData data, String sessionId) {
        return data.sessions().stream()
                .filter(record -> record.id().equals(sessionId))
                .findFirst();
    }

    private SessionIndexData load() throws IOException {
        Files.createDirectories(sessionsDirectory);
        if (!Files.exists(indexFile)) {
            SessionIndexData migrated = migrateExistingSessions();
            save(migrated);
            return migrated;
        }
        return objectMapper.readValue(Files.readString(indexFile, StandardCharsets.UTF_8), SessionIndexData.class);
    }

    private SessionIndexData migrateExistingSessions() throws IOException {
        List<SessionRecord> records = new ArrayList<>();
        for (var summary : SessionCatalog.list(sessionsDirectory)) {
            String time = summary.modifiedAt().toString();
            records.add(new SessionRecord(summary.name(), summary.name(), time, time, false));
        }
        return new SessionIndexData(records);
    }

    private void save(SessionIndexData data) throws IOException {
        Files.createDirectories(sessionsDirectory);
        Files.writeString(
                indexFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }

    private String generateId(SessionIndexData data) {
        Predicate<String> indexed = id -> data.sessions().stream().anyMatch(record -> record.id().equals(id));
        String suffix = randomSuffix();
        String id = "sess_" + ID_TIME_FORMATTER.format(Instant.now()) + "_" + suffix;
        while (indexed.test(id) || SessionCatalog.exists(sessionsDirectory, id)) {
            suffix = randomSuffix();
            id = "sess_" + ID_TIME_FORMATTER.format(Instant.now()) + "_" + suffix;
        }
        return id;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String cleanDisplayName(String displayName, String fallback) {
        if (displayName == null || displayName.isBlank()) {
            return fallback;
        }
        return displayName.trim();
    }

    private String defaultDisplayName(String createdAt) {
        return "会话 " + createdAt;
    }

    private String now() {
        return Instant.now().toString();
    }

    @FunctionalInterface
    private interface Updater {
        SessionRecord update(SessionRecord record);
    }
}
