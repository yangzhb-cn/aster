package com.aster.app.background;

import com.aster.app.background.model.BackgroundTaskEvent;

/**
 * 后台任务事件消费者。
 */
public interface BackgroundTaskEventHandler {
    /**
     * 处理一条后台任务事件。
     */
    void onEvent(BackgroundTaskEvent event);

    static BackgroundTaskEventHandler noop() {
        return ignored -> {
        };
    }
}
