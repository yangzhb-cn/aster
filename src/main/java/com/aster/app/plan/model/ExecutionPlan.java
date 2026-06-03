package com.aster.app.plan.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 DAG 执行计划。
 *
 * <p>它保存原始目标和任务节点，并提供 readyTasks 查询。
 * readyTasks 只返回仍在 PENDING 且全部依赖已完成的节点。</p>
 */
public final class ExecutionPlan {
    private final String task;
    private final List<PlanTask> tasks;
    private final Map<String, PlanTask> byId;

    public ExecutionPlan(String task, List<PlanTask> tasks) {
        this.task = task == null ? "" : task;
        this.tasks = new ArrayList<>(tasks == null ? List.of() : tasks);
        this.byId = new LinkedHashMap<>();
        for (PlanTask planTask : this.tasks) {
            byId.put(planTask.id(), planTask);
        }
    }

    public String task() {
        return task;
    }

    public List<PlanTask> tasks() {
        return tasks;
    }

    public PlanTask task(String id) {
        return byId.get(id);
    }

    /**
     * 返回当前可以执行的 DAG 节点。
     */
    public List<PlanTask> readyTasks() {
        List<PlanTask> ready = new ArrayList<>();
        for (PlanTask task : tasks) {
            if (task.status() != PlanTaskStatus.PENDING) {
                continue;
            }
            if (dependenciesCompleted(task)) {
                ready.add(task);
            }
        }
        return ready;
    }

    /**
     * 判断是否仍有等待执行的任务。
     */
    public boolean hasPending() {
        return tasks.stream().anyMatch(task -> task.status() == PlanTaskStatus.PENDING);
    }

    /**
     * 判断所有任务是否都已完成。
     */
    public boolean allCompleted() {
        return !tasks.isEmpty() && tasks.stream().allMatch(task -> task.status() == PlanTaskStatus.COMPLETED);
    }

    private boolean dependenciesCompleted(PlanTask task) {
        for (String dependencyId : task.dependencies()) {
            PlanTask dependency = byId.get(dependencyId);
            if (dependency == null || dependency.status() != PlanTaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }
}
