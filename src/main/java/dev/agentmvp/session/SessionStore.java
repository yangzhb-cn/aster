package dev.agentmvp.session;

import dev.agentmvp.llm.model.Message;

import java.util.List;

/**
 * session 持久化边界。
 *
 * <p>它只负责保存和读取完整对话历史，不负责压缩。这样以后从内存换成文件、
 * SQLite 或 Redis 时，不需要改 AgentLoop 的核心逻辑。</p>
 */
public interface SessionStore {
    /**
     * 写入一条完整消息。
     */
    void append(Message message);

    /**
     * 读取当前 session 的完整消息列表。
     */
    List<Message> loadMessages();
}
