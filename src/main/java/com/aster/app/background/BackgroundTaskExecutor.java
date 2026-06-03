package com.aster.app.background;

import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.BackgroundTaskEvent;
import com.aster.app.background.model.TaskRun;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 后台任务执行器。
 *
 * <p>它只负责找到匹配的 handler、执行动作、写入 TaskRun，并发布完成或失败事件。
 * 具体任务逻辑不写在这里。</p>
 */
public class BackgroundTaskExecutor {
    private final BackgroundTaskStore store;
    private final List<BackgroundTaskHandler> handlers;
    private final BackgroundTaskEventBus eventBus;

    public BackgroundTaskExecutor(
            BackgroundTaskStore store,
            List<BackgroundTaskHandler> handlers,
            BackgroundTaskEventBus eventBus
    ) {
        this.store = Objects.requireNonNull(store);
        this.handlers = List.copyOf(Objects.requireNonNull(handlers));
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * 执行一次后台任务，并记录运行结果。
     */
    public void execute(BackgroundTask task) {
        String startedAt = Instant.now().toString();
        try {
            BackgroundTaskHandler handler = findHandler(task);
            String message = handler.handle(task);
            TaskRun run = TaskRun.success(task, startedAt, Instant.now().toString(), message);
            store.appendRun(run);
            markOneShotTaskFinished(task, true);
            eventBus.publish(new BackgroundTaskEvent.TaskCompleted(run));
        } catch (Exception e) {
            TaskRun run = TaskRun.failed(task, startedAt, Instant.now().toString(), e.getMessage());
            try {
                store.appendRun(run);
                markOneShotTaskFinished(task, false);
            } catch (IOException ignored) {
                // 运行记录写入失败不能让调度线程崩掉。
            }
            eventBus.publish(new BackgroundTaskEvent.TaskFailed(run));
        }
    }

    private BackgroundTaskHandler findHandler(BackgroundTask task) throws IOException {
        for (BackgroundTaskHandler handler : handlers) {
            if (handler.supports(task.action())) {
                return handler;
            }
        }
        throw new IOException("No background task handler for action: " + task.action().type());
    }

    private void markOneShotTaskFinished(BackgroundTask task, boolean success) throws IOException {
        String type = task.trigger().type();
        if ("immediate".equals(type) || "delay".equals(type)) {
            store.saveTask(success ? task.completed() : task.failed());
        }
    }
}
