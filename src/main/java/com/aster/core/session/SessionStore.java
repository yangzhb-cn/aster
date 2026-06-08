package com.aster.core.session;

import com.aster.llm.text.model.Message;

import java.io.IOException;
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
    void append(Message message) throws IOException;

    /**
     * 读取当前 session 的完整消息列表。
     */
    List<Message> loadMessages() throws IOException;

    /**
     * 记录一次 Agent run 开始。
     *
     * <p>内存版可以不处理；JSONL 版会写入事件，方便审计一次用户请求经历了什么。</p>
     */
    default void recordRunStarted(String userInput) throws IOException {
    }

    /**
     * 记录一次 Agent run 正常结束。
     */
    default void recordRunFinished(String answer) throws IOException {
    }

    /**
     * 记录一次 Agent run 被异常打断。
     */
    default void recordRunInterrupted(String reason) throws IOException {
    }
}
