package com.aster.app.extension;

import com.aster.app.skill.SkillIndexRenderer;
import com.aster.core.hook.AgentHookPoints;

/**
 * 系统提醒扩展。
 *
 * <p>它在 LLM 请求前，把动态 Skill 索引、旧对话摘要和长期记忆
 * 注入最后一条 user 消息开头的 {@code <system-reminder>} 块。</p>
 */
public class SystemReminderExtension implements AsterRuntimeExtension {
    /**
     * 注册请求前系统提醒 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        String skillIndex = new SkillIndexRenderer().render(context.skillRepository().listMetadata());
        context.hookRegistry().register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                new SystemReminderInjectHook(skillIndex, context.memoryStore(), context.memoryPromptRenderer())
        );
    }
}
