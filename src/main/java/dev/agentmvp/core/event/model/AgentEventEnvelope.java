package dev.agentmvp.core.event.model;

/**
 * 带元信息的 Agent 事件信封。
 *
 * <p>AgentLoop 只发布 AgentEvent。AgentEventBus 会把它包装成 Envelope，
 * 再交给 TUI、Web、日志等消费者。这样所有出口都能拿到统一 metadata。</p>
 */
public record AgentEventEnvelope(
        AgentEventMeta meta,
        AgentEvent event
) {
}
