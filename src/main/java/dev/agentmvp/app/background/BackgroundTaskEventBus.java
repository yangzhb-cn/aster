package dev.agentmvp.app.background;

import dev.agentmvp.app.background.model.BackgroundTaskEvent;

import java.util.List;
import java.util.Objects;

/**
 * 后台任务事件总线。
 *
 * <p>它和 AgentEventBus 分开，避免后台任务事件污染主对话事件协议。
 * TUI 当前只用它更新底部状态栏；后续 Web 或日志也可以注册消费者。</p>
 */
public class BackgroundTaskEventBus {
    private final List<BackgroundTaskEventHandler> handlers;

    public BackgroundTaskEventBus(List<BackgroundTaskEventHandler> handlers) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers));
    }

    /**
     * 创建只有一个消费者的后台事件总线。
     */
    public static BackgroundTaskEventBus single(BackgroundTaskEventHandler handler) {
        return new BackgroundTaskEventBus(List.of(Objects.requireNonNull(handler)));
    }

    /**
     * 创建空消费者后台事件总线。
     */
    public static BackgroundTaskEventBus noop() {
        return new BackgroundTaskEventBus(List.of(BackgroundTaskEventHandler.noop()));
    }

    /**
     * 发布后台任务事件。
     */
    public void publish(BackgroundTaskEvent event) {
        for (BackgroundTaskEventHandler handler : handlers) {
            handler.onEvent(event);
        }
    }
}
