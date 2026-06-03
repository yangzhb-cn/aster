package dev.agentmvp.app.background;

import dev.agentmvp.app.background.model.BackgroundTask;
import dev.agentmvp.app.background.model.TaskAction;

/**
 * 空后台任务处理器。
 *
 * <p>它不做业务，只返回一条完成消息。这个 handler 用来验证调度、执行、
 * 记录和通知链路都能跑通。</p>
 */
public class NoopTaskHandler implements BackgroundTaskHandler {
    @Override
    public boolean supports(TaskAction action) {
        return action != null && "noop".equals(action.type());
    }

    @Override
    public String handle(BackgroundTask task) {
        return "noop task completed";
    }
}
