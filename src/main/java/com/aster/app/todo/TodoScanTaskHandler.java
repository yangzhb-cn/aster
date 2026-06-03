package com.aster.app.todo;

import com.aster.app.background.BackgroundTaskHandler;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskAction;
import com.aster.app.todo.model.TodoItem;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 便签待办扫描后台任务处理器。
 *
 * <p>MVP 只做到期提醒：扫描 dueAt 已到期的 pending 待办，推送消息并标记完成。
 * 后续如果要真的让 Agent 自动完成任务，再新增执行型 handler。</p>
 */
public class TodoScanTaskHandler implements BackgroundTaskHandler {
    public static final String ACTION_TYPE = "todo_scan";

    private final TodoStore todoStore;

    public TodoScanTaskHandler(TodoStore todoStore) {
        this.todoStore = Objects.requireNonNull(todoStore);
    }

    @Override
    public boolean supports(TaskAction action) {
        return action != null && ACTION_TYPE.equals(action.type());
    }

    /**
     * 扫描到期待办并返回通知摘要。
     */
    @Override
    public String handle(BackgroundTask task) throws Exception {
        List<TodoItem> dueItems = todoStore.dueItems(Instant.now());
        if (dueItems.isEmpty()) {
            return "";
        }
        StringBuilder message = new StringBuilder("待办到期：");
        for (TodoItem item : dueItems) {
            String result = "已提醒：" + item.content();
            todoStore.complete(item.id(), result);
            message.append("\n- ").append(item.content());
        }
        return message.toString();
    }
}
