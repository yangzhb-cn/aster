package com.aster.app.background;

import com.aster.app.background.model.BackgroundTask;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 后台任务统一入口。
 *
 * <p>AgentRuntime 持有它。以后工具层要创建、查看或取消后台任务时，
 * 也只需要调用这个 manager，不直接碰 scheduler 和 store。</p>
 */
public class BackgroundTaskManager implements AutoCloseable {
    private final BackgroundTaskStore store;
    private final BackgroundTaskScheduler scheduler;

    public BackgroundTaskManager(BackgroundTaskStore store, BackgroundTaskScheduler scheduler) {
        this.store = Objects.requireNonNull(store);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    /**
     * 启动时恢复所有 enabled 任务。
     */
    public void start() throws IOException {
        for (BackgroundTask task : store.listTasks()) {
            if (task.enabled()) {
                scheduler.schedule(task);
            }
        }
    }

    /**
     * 创建并调度一个后台任务。
     */
    public BackgroundTask create(BackgroundTask task) throws IOException {
        store.saveTask(task);
        scheduler.schedule(task);
        return task;
    }

    /**
     * 列出任务定义。
     */
    public List<BackgroundTask> listTasks() throws IOException {
        return store.listTasks();
    }

    /**
     * 取消任务。
     */
    public void cancel(String taskId) throws IOException {
        BackgroundTask task = store.findTask(taskId)
                .orElseThrow(() -> new IOException("background task not found: " + taskId));
        BackgroundTask cancelled = task.cancelled();
        store.saveTask(cancelled);
        scheduler.cancel(taskId);
    }

    @Override
    public void close() {
        scheduler.close();
    }
}
