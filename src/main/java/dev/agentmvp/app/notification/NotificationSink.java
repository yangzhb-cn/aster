package dev.agentmvp.app.notification;

import dev.agentmvp.app.background.model.TaskRun;

/**
 * 后台通知出口。
 *
 * <p>后台任务不直接依赖 TUI。任务完成或失败后只通知这个接口，
 * 具体是更新 TUI 状态栏、写 Web SSE，还是写日志，由不同实现决定。</p>
 */
public interface NotificationSink {
    /**
     * 后台任务成功完成。
     */
    void backgroundTaskCompleted(TaskRun run);

    /**
     * 后台任务执行失败。
     */
    void backgroundTaskFailed(TaskRun run);

    static NotificationSink noop() {
        return new NotificationSink() {
            @Override
            public void backgroundTaskCompleted(TaskRun run) {
            }

            @Override
            public void backgroundTaskFailed(TaskRun run) {
            }
        };
    }
}
