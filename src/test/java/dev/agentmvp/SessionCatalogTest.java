package dev.agentmvp;

import dev.agentmvp.session.SessionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session 文件目录测试。
 */
class SessionCatalogTest {
    @TempDir
    Path tempDir;

    /**
     * 验证只列出 jsonl session 文件，并能按名称定位路径。
     */
    @Test
    void listsJsonlSessions() throws Exception {
        Files.writeString(tempDir.resolve("default.jsonl"), "{}\n");
        Files.writeString(tempDir.resolve("experiment.jsonl"), "{}\n");
        Files.writeString(tempDir.resolve("note.txt"), "ignored");

        var sessions = SessionCatalog.list(tempDir);

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(session -> session.name().equals("default")));
        assertTrue(sessions.stream().anyMatch(session -> session.name().equals("experiment")));
        assertEquals(tempDir.resolve("default.jsonl").normalize(), SessionCatalog.fileFor(tempDir, "default"));
    }

    /**
     * 验证 session 名不能路径穿越。
     */
    @Test
    void rejectsUnsafeSessionName() {
        assertThrows(IllegalArgumentException.class, () -> SessionCatalog.requireValidName("../x"));
        assertThrows(IllegalArgumentException.class, () -> SessionCatalog.requireValidName("has space"));
    }
}
