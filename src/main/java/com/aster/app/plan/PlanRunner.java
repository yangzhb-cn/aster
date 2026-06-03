package com.aster.app.plan;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanRunResult;
import com.aster.app.plan.model.PlanTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 按 DAG 依赖并行执行计划任务。
 *
 * <p>PlanRunner 不知道任务由谁执行。它只负责：
 * 找出 ready 节点、并行调度、写类任务串行、失败后停止继续调度。</p>
 */
public class PlanRunner {
    private static final int MAX_ATTEMPTS = 1;

    /**
     * 执行单个 DAG task 的回调。
     */
    @FunctionalInterface
    public interface TaskExecutor {
        String execute(PlanTask task, ExecutionPlan plan) throws Exception;
    }

    private final TaskExecutor taskExecutor;
    private final int parallelism;
    private final Object writeLock = new Object();

    public PlanRunner(TaskExecutor taskExecutor, int parallelism) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
        this.parallelism = Math.max(1, parallelism);
    }

    /**
     * 执行一份 DAG 计划。
     */
    public PlanRunResult run(ExecutionPlan plan) throws Exception {
        if (plan == null || plan.tasks().isEmpty()) {
            return new PlanRunResult(false, "当前没有可执行计划");
        }

        String failure = null;
        Map<PlanTask, Future<TaskResult>> running = new LinkedHashMap<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(parallelism)) {
            boolean stopScheduling = false;
            while (true) {
                if (!stopScheduling) {
                    for (PlanTask task : plan.readyTasks()) {
                        task.markRunning();
                        running.put(task, pool.submit(() -> runWithRetry(task, plan)));
                    }
                }
                if (running.isEmpty()) {
                    break;
                }

                List<Map.Entry<PlanTask, Future<TaskResult>>> done = waitDone(running);
                for (Map.Entry<PlanTask, Future<TaskResult>> entry : done) {
                    running.remove(entry.getKey());
                    TaskResult result = readResult(entry.getValue());
                    if (result.success()) {
                        entry.getKey().markCompleted(result.output());
                    } else {
                        entry.getKey().markFailed(result.output());
                        if (failure == null) {
                            failure = entry.getKey().id() + " 失败: " + result.output();
                        }
                        stopScheduling = true;
                    }
                }
            }
        }

        if (failure != null) {
            return new PlanRunResult(false, failure);
        }
        if (plan.allCompleted()) {
            return new PlanRunResult(true, "");
        }
        if (plan.hasPending()) {
            return new PlanRunResult(false, "存在未执行任务，请检查依赖或失败任务");
        }
        return new PlanRunResult(true, "");
    }

    private TaskResult runWithRetry(PlanTask task, ExecutionPlan plan) {
        String last = "";
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            task.incrementAttempts();
            try {
                String output = execute(task, plan);
                if (output == null || !output.startsWith("错误:")) {
                    return new TaskResult(true, output == null ? "" : output);
                }
                last = output;
            } catch (Exception e) {
                last = e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }
        return new TaskResult(false, last);
    }

    private String execute(PlanTask task, ExecutionPlan plan) throws Exception {
        if (!task.writeLocked()) {
            return taskExecutor.execute(task, plan);
        }
        synchronized (writeLock) {
            return taskExecutor.execute(task, plan);
        }
    }

    private static List<Map.Entry<PlanTask, Future<TaskResult>>> waitDone(Map<PlanTask, Future<TaskResult>> running) throws InterruptedException {
        while (true) {
            List<Map.Entry<PlanTask, Future<TaskResult>>> done = new ArrayList<>();
            for (Map.Entry<PlanTask, Future<TaskResult>> entry : running.entrySet()) {
                if (entry.getValue().isDone()) {
                    done.add(entry);
                }
            }
            if (!done.isEmpty()) {
                return done;
            }
            Thread.sleep(10);
        }
    }

    private static TaskResult readResult(Future<TaskResult> future) throws ExecutionException, InterruptedException {
        return future.get();
    }

    /**
     * 单个 task 执行后的结果。
     */
    private record TaskResult(boolean success, String output) {
    }
}
