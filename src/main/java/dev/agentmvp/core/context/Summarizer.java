package dev.agentmvp.core.context;

import dev.agentmvp.llm.model.Message;

import java.util.List;

/**
 * 把旧对话消息摘要成一段更短的文本。
 */
public interface Summarizer {
    /**
     * 把旧消息转换成纯文本摘要内容。
     */
    String summarize(List<Message> oldMessages);
}
