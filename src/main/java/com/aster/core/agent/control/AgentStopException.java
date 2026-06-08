package com.aster.core.agent.control;

import com.aster.llm.text.model.Message;

/**
 * 用户主动停止当前 Agent run。
 *
 * <p>它表示可预期的运行控制，不等同于模型、工具或系统异常。
 * partialAssistant 用来保存已经流式输出的可见 assistant 文本。</p>
 */
public class AgentStopException extends RuntimeException {
    private final Message partialAssistant;

    public AgentStopException() {
        this(null);
    }

    public AgentStopException(Message partialAssistant) {
        super("Agent run stopped by user");
        this.partialAssistant = partialAssistant;
    }

    /**
     * 用户停止前已经拼出的 assistant 消息，可能为空。
     */
    public Message partialAssistant() {
        return partialAssistant;
    }

    /**
     * 用户停止前已经产生的可见文本。
     */
    public String partialText() {
        return partialAssistant == null || partialAssistant.content() == null
                ? ""
                : partialAssistant.content();
    }
}
