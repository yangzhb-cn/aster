package com.aster.core.stage;

import com.aster.llm.model.Message;
import com.aster.core.session.SessionStore;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 读取完整 session 历史的内置 Stage。
 *
 * <p>SessionStore 保留原始对话转写；后续 Stage 再决定本轮真正发给 LLM 的消息列表。
 * 这样压缩不会破坏会话恢复、审计和分支。</p>
 */
public class LoadSessionMessagesStage implements Stage {
    private final SessionStore sessionStore;

    public LoadSessionMessagesStage(SessionStore sessionStore) {
        this.sessionStore = Objects.requireNonNull(sessionStore);
    }

    @Override
    public String name() {
        return "load_session_messages";
    }

    /**
     * 读取完整 session 消息。
     */
    public List<Message> load() throws IOException {
        return sessionStore.loadMessages();
    }
}
