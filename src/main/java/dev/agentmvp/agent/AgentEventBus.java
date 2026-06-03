package dev.agentmvp.agent;

import dev.agentmvp.agent.model.AgentEvent;
import dev.agentmvp.agent.model.AgentEventEnvelope;
import dev.agentmvp.agent.model.AgentEventMeta;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 事件总线。
 *
 * <p>AgentLoop 只调用 publish(AgentEvent)。EventBus 负责补齐统一 metadata，
 * 再同步分发给多个消费者。教学版先保持同步分发，保证事件顺序清晰；
 * 后续如果 Hook 很慢，再给特定 handler 外面包异步队列。</p>
 */
public class AgentEventBus {
    private final String sessionName;
    private final List<AgentEventHandler> handlers;

    private String runId = "";
    private long sequence;

    public AgentEventBus(String sessionName, List<AgentEventHandler> handlers) {
        this.sessionName = Objects.requireNonNull(sessionName);
        this.handlers = List.copyOf(Objects.requireNonNull(handlers));
    }

    /**
     * 创建只有一个消费者的事件总线。
     */
    public static AgentEventBus single(String sessionName, AgentEventHandler handler) {
        return new AgentEventBus(sessionName, List.of(Objects.requireNonNull(handler)));
    }

    /**
     * 创建空消费者事件总线，方便测试或纯后台运行。
     */
    public static AgentEventBus noop(String sessionName) {
        return new AgentEventBus(sessionName, List.of(AgentEventHandler.noop()));
    }

    /**
     * 开始一次新的 run。
     *
     * <p>sequence 从 0 重新开始，下一条发布出去的事件会是 sequence=1。</p>
     */
    public synchronized void beginRun() {
        runId = UUID.randomUUID().toString();
        sequence = 0;
    }

    /**
     * 发布一条 AgentEvent。
     */
    public synchronized void publish(AgentEvent event) {
        if (runId == null || runId.isBlank()) {
            beginRun();
        }

        AgentEventEnvelope envelope = new AgentEventEnvelope(
                new AgentEventMeta(
                        UUID.randomUUID().toString(),
                        runId,
                        sessionName,
                        ++sequence,
                        Instant.now()
                ),
                event
        );

        for (AgentEventHandler handler : handlers) {
            handler.onEvent(envelope);
        }
    }
}
