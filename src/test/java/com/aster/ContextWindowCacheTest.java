package com.aster;

import com.aster.core.context.ContextPipeline;
import com.aster.core.context.ContextWindowCache;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.ToolProtocolValidator;
import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.context.model.ContextWindowSnapshot;
import com.aster.core.session.ContextWindowSessionStore;
import com.aster.core.session.SessionStore;
import com.aster.llm.model.Message;
import com.aster.llm.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContextWindowCache 的增量窗口测试。
 *
 * <p>重点验证运行态窗口不再每轮全量读取 session，并且压缩仍按完整 user turn
 * 移动，不会留下半截 tool_call/tool_result。</p>
 */
class ContextWindowCacheTest {
    @Test
    void buildsFromRuntimeWindowWithoutReloadingSession() throws Exception {
        CountingSessionStore delegate = new CountingSessionStore(List.of(Message.system("系统提示")));
        ContextWindowCache cache = ContextWindowCache.from(
                delegate,
                new SimpleTokenEstimator(),
                ignored -> "summary",
                new ContextOptions(1_000, 0.9, 3)
        );
        ContextPipeline pipeline = new ContextPipeline(cache);
        SessionStore store = new ContextWindowSessionStore(delegate, cache);

        store.append(Message.user("你好"));
        pipeline.build();
        store.append(Message.assistant("你好，我在。"));
        pipeline.build();

        assertEquals(1, delegate.loadCount);
        assertEquals(3, cache.processedMessageCount());
    }

    @Test
    void compressesOldCompleteTurnsAndKeepsToolProtocolValid() {
        ContextWindowCache cache = new ContextWindowCache(
                new SimpleTokenEstimator(),
                ignored -> "old turns summarized",
                new ContextOptions(10, 0.1, 1)
        );

        cache.initialize(List.of(
                Message.system("You are Aster."),
                Message.user("Read config."),
                Message.assistantToolCalls(List.of(
                        ToolCall.function("call_old", "read", "{\"path\":\"config.yml\"}")
                )),
                Message.tool("call_old", "database.url is missing"),
                Message.assistant("The config is missing database.url."),
                Message.user("Review fix."),
                Message.assistant("The fix is OK."),
                Message.user("Now continue.")
        ));

        ContextBuildResult result = cache.build();

        assertTrue(result.compressed());
        assertEquals("old turns summarized", result.summary());
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("Review fix.")
        ));
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("Now continue.")
        ));
        assertFalse(result.messages().stream()
                .flatMap(message -> message.toolCalls().stream())
                .anyMatch(call -> "call_old".equals(call.id())));
        assertFalse(result.messages().stream().anyMatch(message -> "call_old".equals(message.toolCallId())));
        ToolProtocolValidator.validate(result.messages());
    }

    @Test
    void restoresRuntimeWindowFromSnapshot() {
        ContextWindowCache cache = new ContextWindowCache(
                new SimpleTokenEstimator(),
                ignored -> "summary",
                new ContextOptions(100, 0.9, 3)
        );
        cache.initialize(List.of(
                Message.system("系统提示"),
                Message.user("最近问题"),
                Message.assistant("最近回答")
        ));
        ContextWindowSnapshot snapshot = cache.snapshot(
                "default",
                "main",
                2,
                "hash-2",
                "system-hash",
                "summary-hash",
                "llm",
                "model"
        );

        ContextWindowCache restored = new ContextWindowCache(
                new SimpleTokenEstimator(),
                ignored -> "summary",
                new ContextOptions(100, 0.9, 3)
        );
        restored.restore(List.of(Message.system("系统提示")), snapshot);

        ContextBuildResult result = restored.build();
        assertEquals(3, result.messages().size());
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("最近问题")
        ));
    }

    @Test
    void appendsAfterSnapshotWithoutResummarizingOldHistory() {
        ContextWindowCache cache = new ContextWindowCache(
                new SimpleTokenEstimator(),
                ignored -> "已保存的旧摘要",
                new ContextOptions(10, 0.1, 1)
        );
        cache.initialize(List.of(
                Message.system("系统提示"),
                Message.user("很早的问题"),
                Message.assistant("很早的回答"),
                Message.user("中间问题"),
                Message.assistant("中间回答"),
                Message.user("最近问题"),
                Message.assistant("最近回答")
        ));
        ContextWindowSnapshot snapshot = cache.snapshot(
                "default",
                "main",
                4,
                "hash-4",
                "system-hash",
                "summary-hash",
                "llm",
                "model"
        );
        AtomicInteger summarizeCalls = new AtomicInteger();
        ContextWindowCache restored = new ContextWindowCache(
                new SimpleTokenEstimator(),
                messages -> {
                    summarizeCalls.incrementAndGet();
                    return "不应该重新生成的摘要";
                },
                new ContextOptions(10_000, 0.9, 3)
        );

        restored.restore(List.of(Message.system("系统提示")), snapshot);
        restored.append(Message.user("恢复后的新问题"));
        ContextBuildResult result = restored.build();

        assertEquals(0, summarizeCalls.get());
        assertEquals("已保存的旧摘要", result.summary());
        assertTrue(result.messages().stream().anyMatch(message ->
                "user".equals(message.role()) && message.content().contains("恢复后的新问题")
        ));
    }

    private static class CountingSessionStore implements SessionStore {
        private final List<Message> messages = new ArrayList<>();
        private int loadCount;

        private CountingSessionStore(List<Message> initialMessages) {
            messages.addAll(initialMessages);
        }

        @Override
        public void append(Message message) {
            messages.add(message);
        }

        @Override
        public List<Message> loadMessages() throws IOException {
            loadCount++;
            return List.copyOf(messages);
        }
    }
}
