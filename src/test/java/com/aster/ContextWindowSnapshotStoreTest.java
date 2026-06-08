package com.aster;

import com.aster.app.runtime.JsonContextWindowSnapshotStore;
import com.aster.core.context.model.ContextWindowSnapshot;
import com.aster.core.context.model.Turn;
import com.aster.core.context.model.TurnType;
import com.aster.llm.text.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文窗口快照 JSON 存储测试。
 */
class ContextWindowSnapshotStoreTest {
    @TempDir
    Path tempDir;

    /**
     * 验证快照保存为可覆盖 JSON 文件。
     */
    @Test
    void overwritesLatestSnapshotJson() throws Exception {
        JsonContextWindowSnapshotStore store = new JsonContextWindowSnapshotStore(new ObjectMapper(), tempDir);
        ContextWindowSnapshot first = snapshot("summary-1", 10);
        ContextWindowSnapshot second = snapshot("summary-2", 11);

        store.save(first);
        store.save(second);

        ContextWindowSnapshot loaded = store.load("default", "main").orElseThrow();
        assertEquals("summary-2", loaded.runningSummary());
        assertEquals(11, loaded.lastSeq());
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .count());
        }
        assertTrue(Files.readString(tempDir.resolve("default.main.json")).contains("summary-2"));
    }

    /**
     * 验证物理删除 session 时可以清掉该 session 的所有上下文快照。
     */
    @Test
    void deletesAllSnapshotsForSession() throws Exception {
        JsonContextWindowSnapshotStore store = new JsonContextWindowSnapshotStore(new ObjectMapper(), tempDir);
        store.save(snapshot("target-main", "target", "main", 10));
        store.save(snapshot("target-dev", "target", "dev", 11));
        store.save(snapshot("other-main", "other", "main", 12));

        store.deleteSession("target");

        assertFalse(Files.exists(tempDir.resolve("target.main.json")));
        assertFalse(Files.exists(tempDir.resolve("target.dev.json")));
        assertTrue(Files.exists(tempDir.resolve("other.main.json")));
    }

    private ContextWindowSnapshot snapshot(String summary, long seq) {
        return snapshot(summary, "default", "main", seq);
    }

    private ContextWindowSnapshot snapshot(String summary, String sessionId, String branchId, long seq) {
        return new ContextWindowSnapshot(
                ContextWindowSnapshot.CURRENT_VERSION,
                sessionId,
                branchId,
                seq,
                "hash-" + seq,
                "system-hash",
                "summary-hash",
                "llm",
                "model",
                summary,
                List.of(new Turn(TurnType.USER_TURN, List.of(Message.user("hello")))),
                "2026-06-07T00:00:00Z"
        );
    }
}
