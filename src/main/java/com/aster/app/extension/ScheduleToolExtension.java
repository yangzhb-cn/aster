package com.aster.app.extension;

import com.aster.app.tool.schedule.ScheduleTool;

/**
 * 自动化用户消息工具扩展。
 *
 * <p>把 schedule 注册给 Agent，让模型可以创建“到点自动向当前 session
 * 发送 user 消息”的任务。</p>
 */
public class ScheduleToolExtension implements AsterRuntimeExtension {
    /**
     * 按当前项目 RuntimeExtension 方式注册 schedule 工具。
     */
    @Override
    public void registerTools(RuntimeExtensionContext context) {
        new ScheduleTool(context.scheduledUserMessageManager()).registerTo(context.toolRegistry());
    }
}
