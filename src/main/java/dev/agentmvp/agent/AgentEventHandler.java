package dev.agentmvp.agent;

import dev.agentmvp.agent.model.AgentEvent;

/**
 * 流式 Agent 事件的消费接口。
 *
 * <p>这样 AgentLoop 不需要关心 TUI 渲染、Web SSE、日志或测试。
 * 主循环只负责发事件，外层决定怎么展示或转发。</p>
 */
public interface AgentEventHandler {
    /**
     * 处理主循环发出的一条事件。
     */
    void onEvent(AgentEvent event);

    static AgentEventHandler noop() {
        return ignored -> {
        };
    }
}
