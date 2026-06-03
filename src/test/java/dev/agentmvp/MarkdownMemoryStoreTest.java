package dev.agentmvp;

import dev.agentmvp.app.memory.MarkdownMemoryStore;
import dev.agentmvp.app.memory.model.MemoryCandidate;
import dev.agentmvp.app.memory.model.MemoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown 长期记忆存储测试。
 */
class MarkdownMemoryStoreTest {
    @TempDir
    Path tempDir;

    /**
     * 验证首次创建时会生成四个固定分类。
     */
    @Test
    void createsMarkdownWithFourAllowedSections() throws Exception {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("long-term-memory.md"));

        String markdown = store.load();

        assertTrue(markdown.contains("## 用户画像"));
        assertTrue(markdown.contains("## 行为偏好"));
        assertTrue(markdown.contains("## 项目动态"));
        assertTrue(markdown.contains("## 外部指针"));
    }

    /**
     * 验证候选记忆会写入对应区块，并按内容去重。
     */
    @Test
    void appendsCandidatesIntoMatchingSectionAndSkipsDuplicates() throws Exception {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("long-term-memory.md"));
        MemoryCandidate candidate = new MemoryCandidate(
                MemoryType.PROJECT_DYNAMIC,
                "项目已经接入后台任务框架。",
                "代码中存在 BackgroundTaskManager"
        );

        assertEquals(1, store.appendCandidates(List.of(candidate)));
        assertEquals(0, store.appendCandidates(List.of(candidate)));

        String markdown = store.load();
        assertTrue(markdown.contains("## 项目动态"));
        assertTrue(markdown.contains("- 项目已经接入后台任务框架。"));
        assertTrue(markdown.contains("证据：代码中存在 BackgroundTaskManager"));
    }

    /**
     * 验证没有证据的候选记忆不能写入。
     */
    @Test
    void rejectsCandidateWithoutEvidence() throws Exception {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("long-term-memory.md"));

        assertThrows(IllegalArgumentException.class, () -> store.appendCandidates(List.of(new MemoryCandidate(
                MemoryType.USER_PROFILE,
                "用户在做 Java 项目。",
                ""
        ))));
    }
}
