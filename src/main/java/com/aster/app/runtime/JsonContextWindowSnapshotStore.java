package com.aster.app.runtime;

import com.aster.core.context.model.ContextWindowSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON 文件版上下文窗口快照存储。
 *
 * <p>每个 session/branch 只有一个最新 JSON 文件。保存时覆盖旧快照，
 * 不做 JSONL 追加，因为快照只是运行态缓存，不承担审计职责。</p>
 */
public class JsonContextWindowSnapshotStore implements ContextWindowSnapshotStore {
    private final ObjectMapper objectMapper;
    private final Path directory;

    public JsonContextWindowSnapshotStore(ObjectMapper objectMapper, Path directory) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.directory = Objects.requireNonNull(directory);
    }

    /**
     * 读取快照文件；文件不存在时返回空。
     */
    @Override
    public Optional<ContextWindowSnapshot> load(String sessionId, String branchId) throws IOException {
        Path file = fileFor(sessionId, branchId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(file.toFile(), ContextWindowSnapshot.class));
    }

    /**
     * 使用临时文件 + move 覆盖保存，避免写入半截 JSON。
     */
    @Override
    public void save(ContextWindowSnapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        Path file = fileFor(snapshot.sessionId(), snapshot.branchId());
        Path tempFile = directory.resolve(file.getFileName() + ".tmp");
        Files.writeString(
                tempFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot),
                StandardCharsets.UTF_8
        );
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 删除指定 session 的所有上下文窗口快照。
     *
     * <p>物理删除 session 时调用；普通归档不调用，因为归档恢复后仍可复用快照继续上下文窗口。</p>
     */
    public void deleteSession(String sessionId) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        String prefix = safe(sessionId) + ".";
        try (var files = Files.list(directory)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(prefix))
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private Path fileFor(String sessionId, String branchId) {
        return directory.resolve(safe(sessionId) + "." + safe(branchId) + ".json");
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
