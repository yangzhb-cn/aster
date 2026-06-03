package dev.agentmvp.app.background;

import dev.agentmvp.app.background.model.BackgroundTask;
import dev.agentmvp.app.background.model.TaskAction;

/**
 * 后台任务动作处理器。
 *
 * <p>不同 action.type 对应不同 handler。框架只负责调度和分发，
 * 不关心具体任务是通知、长期记忆还是工具调用。</p>
 */
public interface BackgroundTaskHandler {
    /**
     * 当前 handler 是否支持这个动作。
     */
    boolean supports(TaskAction action);

    /**
     * 执行后台任务动作。
     */
    String handle(BackgroundTask task) throws Exception;
}
