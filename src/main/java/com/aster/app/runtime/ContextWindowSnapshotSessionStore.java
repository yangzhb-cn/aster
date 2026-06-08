package com.aster.app.runtime;

import com.aster.core.context.ContextWindowCache;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionStore;
import com.aster.core.session.model.SessionMessageRecord;
import com.aster.llm.text.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 写入 session 后同步更新上下文窗口快照的 SessionStore。
 *
 * <p>JSONL 仍然是事实来源；本类只负责在 message_appended 成功后，
 * 把 ContextWindowCache 的最新运行态窗口覆盖写入 snapshot JSON。</p>
 */
public class ContextWindowSnapshotSessionStore implements SessionStore {
    private final List<Message> bootstrapMessages;
    private final JsonlSessionStore delegate;
    private final ContextWindowCache contextWindowCache;
    private final ContextWindowSnapshotStore snapshotStore;
    private final SnapshotMetadata metadata;

    public ContextWindowSnapshotSessionStore(
            List<Message> bootstrapMessages,
            JsonlSessionStore delegate,
            ContextWindowCache contextWindowCache,
            ContextWindowSnapshotStore snapshotStore,
            SnapshotMetadata metadata
    ) {
        this.bootstrapMessages = List.copyOf(bootstrapMessages);
        this.delegate = Objects.requireNonNull(delegate);
        this.contextWindowCache = Objects.requireNonNull(contextWindowCache);
        this.snapshotStore = Objects.requireNonNull(snapshotStore);
        this.metadata = Objects.requireNonNull(metadata);
    }

    /**
     * 先写 JSONL，再更新运行态窗口，最后覆盖保存 snapshot。
     */
    @Override
    public synchronized void append(Message message) throws IOException {
        delegate.append(message);
        SessionMessageRecord record = delegate.lastAppendedMessageRecord();
        contextWindowCache.append(message);
        if (record != null) {
            saveSnapshot(record.seq(), record.hash());
        }
    }

    /**
     * 读取完整历史，供长期记忆等非请求上下文场景使用。
     */
    @Override
    public synchronized List<Message> loadMessages() throws IOException {
        List<Message> messages = new ArrayList<>(bootstrapMessages);
        messages.addAll(delegate.loadMessages());
        return List.copyOf(messages);
    }

    @Override
    public synchronized void recordRunStarted(String userInput) throws IOException {
        delegate.recordRunStarted(userInput);
    }

    @Override
    public synchronized void recordRunFinished(String answer) throws IOException {
        delegate.recordRunFinished(answer);
    }

    @Override
    public synchronized void recordRunInterrupted(String reason) throws IOException {
        delegate.recordRunInterrupted(reason);
    }

    /**
     * 保存当前上下文窗口快照。
     */
    public synchronized void saveSnapshot(long lastSeq, String lastHash) throws IOException {
        snapshotStore.save(contextWindowCache.snapshot(
                metadata.sessionId(),
                metadata.branchId(),
                lastSeq,
                lastHash,
                metadata.systemPromptHash(),
                metadata.summaryPromptHash(),
                metadata.summarizer(),
                metadata.model()
        ));
    }

    /**
     * 快照校验和写入需要的稳定元信息。
     */
    public record SnapshotMetadata(
            String sessionId,
            String branchId,
            String systemPromptHash,
            String summaryPromptHash,
            String summarizer,
            String model
    ) {
    }
}
