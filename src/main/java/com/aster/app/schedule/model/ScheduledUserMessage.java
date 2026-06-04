package com.aster.app.schedule.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * 自动化用户消息定义。
 *
 * <p>它不直接执行业务逻辑，只描述“什么时候向哪个 session 发送哪条 user 消息”。
 * 真正执行仍走 AgentRuntime.submit，从而复用 AgentLoop、Tool、HITL、Event 和 Session。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScheduledUserMessage(
        String id,
        String sessionId,
        String name,
        String content,
        boolean enabled,
        ScheduleStatus status,
        ScheduleTrigger trigger,
        String nextRunAt,
        String lastRunAt,
        String createdAt,
        String updatedAt
) {
    /**
     * 创建新的自动化用户消息。
     */
    public static ScheduledUserMessage create(
            String sessionId,
            String name,
            String content,
            ScheduleTrigger trigger,
            String nextRunAt
    ) {
        String now = Instant.now().toString();
        return new ScheduledUserMessage(
                "schedule_" + UUID.randomUUID(),
                sessionId,
                blankToDefault(name, "定时用户消息"),
                content,
                true,
                ScheduleStatus.ACTIVE,
                trigger,
                nextRunAt,
                null,
                now,
                now
        );
    }

    /**
     * 返回取消后的定义。
     */
    public ScheduledUserMessage cancelled() {
        return new ScheduledUserMessage(
                id,
                sessionId,
                name,
                content,
                false,
                ScheduleStatus.CANCELLED,
                trigger,
                nextRunAt,
                lastRunAt,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回一次性任务完成后的定义。
     */
    public ScheduledUserMessage completed(String finishedAt) {
        return new ScheduledUserMessage(
                id,
                sessionId,
                name,
                content,
                false,
                ScheduleStatus.COMPLETED,
                trigger,
                null,
                finishedAt,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回派发失败后的定义，避免到期时间反复触发忙循环。
     */
    public ScheduledUserMessage failed(String finishedAt) {
        return new ScheduledUserMessage(
                id,
                sessionId,
                name,
                content,
                false,
                ScheduleStatus.FAILED,
                trigger,
                null,
                finishedAt,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回重复任务下一次执行时间更新后的定义。
     */
    public ScheduledUserMessage rescheduled(String finishedAt, String nextRunAt) {
        return new ScheduledUserMessage(
                id,
                sessionId,
                name,
                content,
                true,
                ScheduleStatus.ACTIVE,
                trigger,
                nextRunAt,
                finishedAt,
                createdAt,
                Instant.now().toString()
        );
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
