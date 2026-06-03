package dev.agentmvp.agent;

import dev.agentmvp.agent.model.AgentEventEnvelope;

/**
 * 带元信息的 Agent 事件消费接口。
 *
 * <p>AgentLoop 不再直接绑定 TUI。事件先经过 AgentEventBus 包装成 Envelope，
 * 再交给 TUI、Web SSE、日志或测试处理。</p>
 */
public interface AgentEventHandler {
    /**
     * 处理主循环发出的一条带 metadata 的事件。
     */
    void onEvent(AgentEventEnvelope envelope);

    static AgentEventHandler noop() {
        return ignored -> {
        };
    }
}
