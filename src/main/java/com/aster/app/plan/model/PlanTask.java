package com.aster.app.plan.model;

import java.util.List;
import java.util.Objects;

/**
 * DAG 中的单个任务节点。
 *
 * <p>节点保存依赖、状态、执行结果和尝试次数。执行器只根据状态和依赖调度，
 * 具体任务由上层传入的 TaskExecutor 完成。</p>
 */
public final class PlanTask {
    private final String id;
    private final String description;
    private final PlanTaskType type;
    private final List<String> dependencies;
    private PlanTaskStatus status = PlanTaskStatus.PENDING;
    private String result = "";
    private String error = "";
    private int attempts;

    public PlanTask(String id, String description, PlanTaskType type, List<String> dependencies) {
        this.id = requireText(id, "id");
        this.description = requireText(description, "description");
        this.type = Objects.requireNonNull(type);
        this.dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public PlanTaskType type() {
        return type;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public PlanTaskStatus status() {
        return status;
    }

    public String result() {
        return result;
    }

    public String error() {
        return error;
    }

    public int attempts() {
        return attempts;
    }

    /**
     * 判断任务是否需要写锁串行执行。
     */
    public boolean writeLocked() {
        return type == PlanTaskType.FILE_WRITE || type == PlanTaskType.COMMAND;
    }

    /**
     * 标记任务进入运行中状态。
     */
    public void markRunning() {
        status = PlanTaskStatus.RUNNING;
    }

    /**
     * 标记任务成功完成。
     */
    public void markCompleted(String result) {
        status = PlanTaskStatus.COMPLETED;
        this.result = result == null ? "" : result;
        this.error = "";
    }

    /**
     * 标记任务失败。
     */
    public void markFailed(String error) {
        status = PlanTaskStatus.FAILED;
        this.error = error == null ? "" : error;
        this.result = this.error;
    }

    /**
     * 记录一次执行尝试。
     */
    public void incrementAttempts() {
        attempts++;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
