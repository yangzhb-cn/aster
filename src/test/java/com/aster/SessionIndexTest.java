package com.aster;

import com.aster.core.session.SessionIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session 元信息索引测试。
 */
class SessionIndexTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 验证创建、重命名、刷新和归档只影响 index.json，不删除 JSONL 文件。
     */
    @Test
    void managesSessionMetadataWithoutDeletingJsonl() throws Exception {
        SessionIndex index = new SessionIndex(objectMapper, tempDir);

        var created = index.create("教学会话");
        Files.writeString(tempDir.resolve(created.id() + ".jsonl"), "{}\n");

        assertTrue(created.id().matches("sess_\\d{8}_\\d{6}_[a-f0-9]{8}"));
        assertEquals("教学会话", index.listActive().getFirst().displayName());

        var renamed = index.rename(created.id(), "重命名会话");
        assertEquals("重命名会话", renamed.displayName());

        index.touch(created.id());
        index.archive(created.id());

        assertTrue(index.listActive().isEmpty());
        assertTrue(Files.exists(tempDir.resolve(created.id() + ".jsonl")));
        assertTrue(objectMapper.readTree(Files.readString(tempDir.resolve("index.json")))
                .get("sessions")
                .get(0)
                .get("archived")
                .asBoolean());
    }

    /**
     * 验证首次读取会把已有 jsonl 文件迁移成未归档索引记录。
     */
    @Test
    void migratesExistingJsonlSessionsWhenIndexIsMissing() throws Exception {
        Files.writeString(tempDir.resolve("default.jsonl"), "{}\n");
        Files.writeString(tempDir.resolve("legacy.jsonl"), "{}\n");

        SessionIndex index = new SessionIndex(objectMapper, tempDir);

        var sessions = index.listActive();
        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(session -> session.id().equals("default")));
        assertTrue(sessions.stream().anyMatch(session -> session.displayName().equals("legacy")));
        assertFalse(sessions.stream().anyMatch(session -> session.archived()));
    }

    /**
     * 验证显式打开一个已归档 session 时，会恢复到 active 列表。
     */
    @Test
    void ensureRestoresArchivedSession() throws Exception {
        SessionIndex index = new SessionIndex(objectMapper, tempDir);
        var created = index.create("默认会话");

        index.archive(created.id());
        assertTrue(index.listActive().isEmpty());

        var restored = index.ensure(created.id(), "不会覆盖展示名");

        assertFalse(restored.archived());
        assertEquals("默认会话", restored.displayName());
        assertEquals(created.id(), index.listActive().getFirst().id());
    }

    /**
     * 验证物理删除只允许已归档 session，并会删除 JSONL 文件。
     */
    @Test
    void physicallyDeletesArchivedSession() throws Exception {
        SessionIndex index = new SessionIndex(objectMapper, tempDir);
        var created = index.create("待删除会话");
        Path jsonl = tempDir.resolve(created.id() + ".jsonl");
        Files.writeString(jsonl, "{}\n");

        index.archive(created.id());
        assertEquals(1, index.listArchived().size());

        index.deletePermanently(created.id());

        assertTrue(index.listArchived().isEmpty());
        assertFalse(Files.exists(jsonl));
    }
}
