package com.aster.app.background.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * 后台任务定义。
 *
 * <p>它只描述任务本身：名字、是否启用、触发方式和动作。
 * 每次真正执行的结果会写成 TaskRun，避免把任务定义和执行历史混在一起。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BackgroundTask(
        String id,
        String name,
        boolean enabled,
        TaskStatus status,
        TaskTrigger trigger,
        TaskAction action,
        String createdAt,
        String updatedAt
) {
    /**
     * 创建一个新的后台任务定义。
     */
    public static BackgroundTask create(String name, TaskTrigger trigger, TaskAction action) {
        String now = Instant.now().toString();
        return new BackgroundTask(
                "task_" + UUID.randomUUID(),
                name,
                true,
                TaskStatus.ACTIVE,
                trigger,
                action,
                now,
                now
        );
    }

    /**
     * 返回取消后的任务定义。
     */
    public BackgroundTask cancelled() {
        return new BackgroundTask(
                id,
                name,
                false,
                TaskStatus.CANCELLED,
                trigger,
                action,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回一次性任务成功完成后的定义。
     */
    public BackgroundTask completed() {
        return withFinalStatus(TaskStatus.COMPLETED);
    }

    /**
     * 返回一次性任务失败后的定义。
     */
    public BackgroundTask failed() {
        return withFinalStatus(TaskStatus.FAILED);
    }

    private BackgroundTask withFinalStatus(TaskStatus finalStatus) {
        return new BackgroundTask(
                id,
                name,
                false,
                finalStatus,
                trigger,
                action,
                createdAt,
                Instant.now().toString()
        );
    }
}
