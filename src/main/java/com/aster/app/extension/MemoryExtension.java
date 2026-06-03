package com.aster.app.extension;

import com.aster.app.memory.LongTermMemoryInjectHook;
import com.aster.app.memory.MemoryExtractionHook;
import com.aster.core.hook.AgentHookPoints;

/**
 * 长期记忆扩展。
 *
 * <p>它负责请求前注入长期记忆，并在 run 结束后提交记忆抽取后台任务。</p>
 */
public class MemoryExtension implements AsterRuntimeExtension {
    /**
     * 注册长期记忆相关 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        context.hookRegistry().register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                new LongTermMemoryInjectHook(context.memoryStore(), context.memoryPromptRenderer())
        );
        context.hookRegistry().register(
                AgentHookPoints.AFTER_RUN,
                new MemoryExtractionHook(context.backgroundTaskManager())
        );
    }
}
