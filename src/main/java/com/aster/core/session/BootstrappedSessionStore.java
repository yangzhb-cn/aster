package com.aster.core.session;

import com.aster.llm.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 带启动消息的 SessionStore 包装器。
 *
 * <p>system prompt 和 Skill 索引属于当前运行环境，不属于用户历史。
 * 所以它们不写入 JSONL，只在 loadMessages() 时拼到持久化消息前面。</p>
 */
public class BootstrappedSessionStore implements SessionStore {
    private final List<Message> bootstrapMessages;
    private final SessionStore delegate;

    public BootstrappedSessionStore(List<Message> bootstrapMessages, SessionStore delegate) {
        this.bootstrapMessages = List.copyOf(bootstrapMessages);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void append(Message message) throws IOException {
        delegate.append(message);
    }

    @Override
    public List<Message> loadMessages() throws IOException {
        List<Message> messages = new ArrayList<>(bootstrapMessages);
        messages.addAll(delegate.loadMessages());
        return List.copyOf(messages);
    }

    @Override
    public void recordRunStarted(String userInput) throws IOException {
        delegate.recordRunStarted(userInput);
    }

    @Override
    public void recordRunFinished(String answer) throws IOException {
        delegate.recordRunFinished(answer);
    }

    @Override
    public void recordRunInterrupted(String reason) throws IOException {
        delegate.recordRunInterrupted(reason);
    }
}
