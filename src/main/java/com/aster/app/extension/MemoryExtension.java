package com.aster.app.extension;

import com.aster.app.memory.MemoryExtractionHook;
import com.aster.core.hook.AgentHookPoints;

/**
 * 长期记忆扩展。
 *
 * <p>它负责在 run 结束后提交记忆抽取后台任务。
 * 请求前注入统一交给 SystemReminderExtension。</p>
 */
public class MemoryExtension implements AsterRuntimeExtension {
    /**
     * 注册长期记忆抽取 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        context.hookRegistry().register(
                AgentHookPoints.AFTER_RUN,
                new MemoryExtractionHook(context.backgroundTaskManager())
        );
    }
}
