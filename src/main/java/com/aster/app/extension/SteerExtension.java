package com.aster.app.extension;

import com.aster.core.hook.AgentHookPoints;
import com.aster.llm.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行中引导扩展。
 *
 * <p>它把当前 run control 中的 steer 文本注入下一次 LLM 请求，
 * 但不写入 session，避免破坏工具调用消息协议。</p>
 */
public class SteerExtension implements AsterRuntimeExtension {
    /**
     * 注册运行中引导注入 Hook。
     */
    @Override
    public void registerHooks(RuntimeExtensionContext context) {
        context.hookRegistry().register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                hookContext -> {
                    List<String> steers = hookContext.control().drainSteers();
                    if (steers.isEmpty()) {
                        return hookContext;
                    }
                    List<Message> messages = new ArrayList<>(hookContext.messages());
                    messages.add(Message.user(renderSteerMessage(steers)));
                    return hookContext.withMessages(messages);
                }
        );
    }

    /**
     * 渲染运行中引导消息，作为本轮临时 user message 发送给模型。
     */
    private String renderSteerMessage(List<String> steers) {
        StringBuilder text = new StringBuilder("""
                [运行中用户引导]
                用户在 Agent 工作过程中追加了以下要求。请在不破坏已完成工具结果的前提下，优先按这些最新要求调整下一步回答或工具使用：
                """);
        for (String steer : steers) {
            text.append("- ").append(steer).append("\n");
        }
        return text.toString().stripTrailing();
    }
}
