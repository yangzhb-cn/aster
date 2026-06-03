package dev.agentmvp.agent.model;

import java.time.Instant;

/**
 * Agent 事件的统一元信息。
 *
 * <p>事件本体只描述“发生了什么”，meta 描述“这件事属于哪次运行、哪个 session、
 * 第几条事件、什么时候发生”。Web SSE、审计日志和事件回放都会依赖这些字段。</p>
 */
public record AgentEventMeta(
        String eventId,
        String runId,
        String sessionName,
        long sequence,
        Instant timestamp
) {
}
