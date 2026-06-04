package com.aster.core.session;

import com.aster.core.context.ContextWindowCache;
import com.aster.llm.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 带上下文窗口增量更新的 SessionStore 装饰器。
 *
 * <p>完整历史仍由 delegate 负责读写；本装饰器只在 append 成功后通知
 * ContextWindowCache。这样 ContextPipeline 可以直接使用运行态窗口，
 * 其它需要完整历史的后台能力仍可通过 loadMessages 读取 delegate。</p>
 */
public class ContextWindowSessionStore implements SessionStore {
    private final SessionStore delegate;
    private final ContextWindowCache contextWindowCache;

    public ContextWindowSessionStore(SessionStore delegate, ContextWindowCache contextWindowCache) {
        this.delegate = Objects.requireNonNull(delegate);
        this.contextWindowCache = Objects.requireNonNull(contextWindowCache);
    }

    /**
     * 先写入完整 session，成功后再更新运行态窗口。
     */
    @Override
    public void append(Message message) throws IOException {
        delegate.append(message);
        contextWindowCache.append(message);
    }

    /**
     * 读取完整历史；用于审计、长期记忆抽取等非请求上下文场景。
     */
    @Override
    public List<Message> loadMessages() throws IOException {
        return delegate.loadMessages();
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
