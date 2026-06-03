package dev.agentmvp.app.background;

import dev.agentmvp.app.background.model.BackgroundTaskEvent;
import dev.agentmvp.app.notification.NotificationSink;

import java.util.Objects;

/**
 * 把后台任务事件转成通知。
 *
 * <p>这是策略 B 的关键：后台任务完成后不插入对话历史，
 * 只通知外层更新状态栏。完整记录已经写入 runs.jsonl。</p>
 */
public class BackgroundTaskNotificationHandler implements BackgroundTaskEventHandler {
    private final NotificationSink notificationSink;

    public BackgroundTaskNotificationHandler(NotificationSink notificationSink) {
        this.notificationSink = Objects.requireNonNull(notificationSink);
    }

    @Override
    public void onEvent(BackgroundTaskEvent event) {
        if (event instanceof BackgroundTaskEvent.TaskCompleted completed) {
            notificationSink.backgroundTaskCompleted(completed.run());
            return;
        }
        if (event instanceof BackgroundTaskEvent.TaskFailed failed) {
            notificationSink.backgroundTaskFailed(failed.run());
        }
    }
}
