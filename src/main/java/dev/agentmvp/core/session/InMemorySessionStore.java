package dev.agentmvp.core.session;

import dev.agentmvp.llm.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存版 session 存储。
 *
 * <p>它保存完整原始消息历史；真正决定“发给模型多少上下文”的是 ContextPipeline，
 * 不是 SessionStore。这能把持久化和上下文压缩两个职责分开。</p>
 */
public class InMemorySessionStore implements SessionStore {
    private final List<Message> messages = new ArrayList<>();

    /**
     * 追加一条原始消息到当前 session。
     */
    @Override
    public void append(Message message) {
        messages.add(message);
    }

    /**
     * 读取完整 session 历史，返回副本防止外部直接修改内部列表。
     */
    @Override
    public List<Message> loadMessages() {
        // SessionStore 保留完整原始对话转写；ContextPipeline 决定本轮实际发什么。
        return List.copyOf(messages);
    }
}
