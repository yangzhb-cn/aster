package com.aster.app.background.model;

/**
 * 后台任务事件。
 *
 * <p>后台任务执行完成后通过事件通知外部。TUI、Web、日志都可以消费这些事件，
 * 但它们不应该反过来影响任务执行线程。</p>
 */
public sealed interface BackgroundTaskEvent permits
        BackgroundTaskEvent.TaskCompleted,
        BackgroundTaskEvent.TaskFailed {
    /**
     * 后台任务执行成功。
     */
    record TaskCompleted(TaskRun run) implements BackgroundTaskEvent {
    }

    /**
     * 后台任务执行失败。
     */
    record TaskFailed(TaskRun run) implements BackgroundTaskEvent {
    }
}
