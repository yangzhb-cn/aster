package com.aster.app.background;

import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskAction;

import java.util.Map;

/**
 * 提醒后台任务处理器。
 *
 * <p>它只负责从 TaskAction.params 里取出提醒文本并作为执行结果返回。
 * 真正发到 TUI、Web 或 IM 的动作由 NotificationSink 完成。</p>
 */
public class ReminderTaskHandler implements BackgroundTaskHandler {
    public static final String ACTION_TYPE = "reminder";

    @Override
    public boolean supports(TaskAction action) {
        return action != null && ACTION_TYPE.equals(action.type());
    }

    /**
     * 返回提醒文本，供后台任务通知出口发送给用户。
     */
    @Override
    public String handle(BackgroundTask task) {
        Map<String, Object> params = task.action().params();
        String text = stringParam(params, "text");
        if (text.isBlank()) {
            text = stringParam(params, "message");
        }
        if (text.isBlank()) {
            return "提醒时间到了。";
        }
        return text;
    }

    private String stringParam(Map<String, Object> params, String name) {
        if (params == null) {
            return "";
        }
        Object value = params.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
