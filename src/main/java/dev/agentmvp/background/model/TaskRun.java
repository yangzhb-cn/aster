package dev.agentmvp.background.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * 后台任务的一次执行记录。
 *
 * <p>用户以后问“后台任务刚才做了什么”，Agent 可以读取 runs.jsonl，
 * 根据这些记录进行回答。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TaskRun(
        String id,
        String taskId,
        String taskName,
        TaskRunStatus status,
        String message,
        String startedAt,
        String finishedAt
) {
    /**
     * 创建成功执行记录。
     */
    public static TaskRun success(BackgroundTask task, String startedAt, String finishedAt, String message) {
        return new TaskRun(
                "run_" + UUID.randomUUID(),
                task.id(),
                task.name(),
                TaskRunStatus.SUCCESS,
                message,
                startedAt,
                finishedAt
        );
    }

    /**
     * 创建失败执行记录。
     */
    public static TaskRun failed(BackgroundTask task, String startedAt, String finishedAt, String message) {
        return new TaskRun(
                "run_" + UUID.randomUUID(),
                task.id(),
                task.name(),
                TaskRunStatus.FAILED,
                message,
                startedAt,
                finishedAt
        );
    }
}
