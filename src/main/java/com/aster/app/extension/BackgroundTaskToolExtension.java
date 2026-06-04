package com.aster.app.extension;

import com.aster.app.tool.background.BackgroundTaskTool;

/**
 * 后台任务工具扩展。
 *
 * <p>把 background_task 注册给 Agent，让模型可以创建、查看和取消系统后台任务与延时提醒。</p>
 */
public class BackgroundTaskToolExtension implements AsterRuntimeExtension {
    /**
     * 按当前项目 RuntimeExtension 方式注册后台任务管理工具。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) {
        new BackgroundTaskTool(context.backgroundTaskManager()).registerTo(context.toolRegistry());
    }
}
