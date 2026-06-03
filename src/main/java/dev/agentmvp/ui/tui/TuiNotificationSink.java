package dev.agentmvp.ui.tui;

import dev.agentmvp.app.background.model.TaskRun;
import dev.agentmvp.app.notification.NotificationSink;

import java.util.Objects;

/**
 * TUI 版后台通知出口。
 *
 * <p>它不直接改 blocks，也不插入消息，只调用 AgentTuiWindow 的排队方法，
 * 最终由 TUI 主线程统一刷新底部状态栏。</p>
 */
public class TuiNotificationSink implements NotificationSink {
    private final AgentTuiWindow window;

    public TuiNotificationSink(AgentTuiWindow window) {
        this.window = Objects.requireNonNull(window);
    }

    @Override
    public void backgroundTaskCompleted(TaskRun run) {
        window.showBackgroundTaskCompleted(run.taskName());
    }

    @Override
    public void backgroundTaskFailed(TaskRun run) {
        window.showBackgroundTaskFailed(run.taskName());
    }
}
