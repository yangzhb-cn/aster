package com.aster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.ToolCall;
import com.aster.core.session.BootstrappedSessionStore;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionReplayer;
import com.aster.core.session.model.SessionReplayResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSONL session 持久化测试。
 *
 * <p>目标不是只保存 messages，而是验证事件日志可以恢复、分支、审计，
 * 并且不会把基础 system prompt 这类启动环境写进用户历史。</p>
 */
class JsonlSessionStoreTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 验证消息会以 JSONL 事件形式落盘，并能重新打开恢复。
     */
    @Test
    void persistsMessagesAsJsonlEvents() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore store = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        store.recordRunStarted("你好");
        store.append(Message.user("你好"));
        store.append(Message.assistant("你好，我在。"));
        store.recordRunFinished("你好，我在。");

        JsonlSessionStore reopened = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        assertEquals(List.of(
                Message.user("你好"),
                Message.assistant("你好，我在。")
        ), reopened.loadMessages());
        var records = reopened.loadMessageRecords();
        assertEquals(2, records.size());
        assertEquals("你好", records.get(0).message().content());
        assertTrue(records.get(0).seq() > 0);
        assertTrue(records.get(0).hash() != null && !records.get(0).hash().isBlank());

        String jsonl = Files.readString(file);
        assertTrue(jsonl.contains("\"type\":\"run_started\""));
        assertTrue(jsonl.contains("\"type\":\"message_appended\""));
        assertTrue(jsonl.contains("\"prevHash\""));
        assertTrue(jsonl.contains("\"hash\""));
    }

    /**
     * 验证上下文快照恢复后，可以只读取快照进度之后的新消息。
     */
    @Test
    void loadsMessageRecordsAfterSnapshotSeq() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore store = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        store.append(Message.user("旧问题"));
        store.append(Message.assistant("旧回答"));
        long snapshotSeq = store.lastAppendedMessageRecord().seq();
        String snapshotHash = store.lastAppendedMessageRecord().hash();
        store.recordRunFinished("旧回答");
        store.append(Message.user("新问题"));

        JsonlSessionStore reopened = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        var records = reopened.loadMessageRecordsAfter(snapshotSeq);
        assertTrue(reopened.containsEvent(snapshotSeq, snapshotHash));
        assertEquals(1, records.size());
        assertEquals("新问题", records.getFirst().message().content());
    }

    /**
     * 验证基础 system prompt 只作为启动消息参与上下文，不写入 JSONL。
     */
    @Test
    void keepsBootstrapMessagesOutOfJsonlFile() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore persistentStore = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );
        BootstrappedSessionStore store = new BootstrappedSessionStore(
                List.of(Message.system("系统提示")),
                persistentStore
        );

        store.append(Message.user("用户消息"));

        assertEquals(List.of(
                Message.system("系统提示"),
                Message.user("用户消息")
        ), store.loadMessages());
        assertFalse(Files.readString(file).contains("系统提示"));
    }

    /**
     * 验证分支不会复制整份历史，而是从父分支 forkSeq 处继承。
     */
    @Test
    void replaysBranchFromForkSeq() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore main = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );
        main.append(Message.user("base"));

        main.createBranch("try-refactor", main.currentSeq());
        main.append(Message.user("main-only"));

        JsonlSessionStore branch = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                "try-refactor"
        );
        branch.append(Message.user("branch-only"));

        assertEquals(List.of(
                Message.user("base"),
                Message.user("main-only")
        ), main.loadMessages());
        assertEquals(List.of(
                Message.user("base"),
                Message.user("branch-only")
        ), branch.loadMessages());
    }

    /**
     * 验证恢复时如果尾部只有 assistant.tool_calls，没有对应 tool 结果，会回退到最后合法点。
     */
    @Test
    void trimsIncompleteToolCallTailDuringReplay() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore store = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        store.append(Message.user("读取文件"));
        store.append(Message.assistantToolCalls(List.of(
                ToolCall.function("call_1", "read", "{\"path\":\"README.md\"}")
        )));

        SessionReplayResult result = store.replay();

        assertTrue(result.recovered());
        assertEquals(List.of(Message.user("读取文件")), result.messages());
        assertTrue(result.recoveryReason().contains("unanswered tool_call"));
    }

    /**
     * 验证历史文件里旧的 reasoning-only assistant 也能恢复成合法消息。
     */
    @Test
    void recoversReasoningOnlyAssistantAsContent() throws Exception {
        Path file = tempDir.resolve("sessions/default.jsonl");
        JsonlSessionStore store = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );
        store.append(Message.user("你好"));
        Files.writeString(file, """
                {"seq":99,"eventId":"legacy","sessionId":"default","branchId":"main","type":"message_appended","createdAt":"2026-06-02T00:00:00Z","message":{"role":"assistant","reasoning_content":"旧 thinking 内容"}}
                """, java.nio.file.StandardOpenOption.APPEND);

        JsonlSessionStore reopened = new JsonlSessionStore(
                objectMapper,
                file,
                "default",
                SessionReplayer.MAIN_BRANCH
        );

        assertEquals("旧 thinking 内容", reopened.loadMessages().get(1).content());
        assertEquals(null, reopened.loadMessages().get(1).reasoningContent());
    }
}
