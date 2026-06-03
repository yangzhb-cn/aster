package com.aster.app.extension;

import com.aster.app.tool.todo.TodoTool;

/**
 * 便签待办工具扩展。
 *
 * <p>把 todo 注册给 Agent，让 Agent 能读写 Web 右栏同一份待办清单。</p>
 */
public class TodoToolExtension implements AsterRuntimeExtension {
    /**
     * 按当前项目 RuntimeExtension 方式注册 todo 工具。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) {
        new TodoTool(context.objectMapper(), context.todoStore()).registerTo(context.toolRegistry());
    }
}
