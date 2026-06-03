package com.aster.core.hook;

import com.aster.core.agent.control.AgentRunControl;
import com.aster.llm.model.Message;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM 请求前 Hook 上下文。
 *
 * <p>messages 是当前即将发给模型的消息列表。tools 是当前暴露给模型的工具 Schema。
 * contextSummary 是旧对话压缩后的摘要，只属于本次请求。
 * control 是当前 run 的控制信号。Hook 可以返回新上下文，用于系统提醒注入、
 * 运行中引导、工具隐藏、权限策略等场景。</p>
 */
public record BeforeLlmRequestContext(
        String sessionName,
        String runId,
        int round,
        String model,
        int maxContextTokens,
        String contextSummary,
        AgentRunControl control,
        List<Message> messages,
        List<Map<String, Object>> tools
) {
    public BeforeLlmRequestContext {
        control = Objects.requireNonNull(control);
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }

    /**
     * 返回替换消息列表后的上下文。
     */
    public BeforeLlmRequestContext withMessages(List<Message> newMessages) {
        return new BeforeLlmRequestContext(
                sessionName,
                runId,
                round,
                model,
                maxContextTokens,
                contextSummary,
                control,
                newMessages,
                tools
        );
    }

    /**
     * 返回替换工具列表后的上下文。
     *
     * <p>后续如果要做“某些工具需要审批后才暴露给模型”，
     * 或者“当前任务只允许 read，不允许 bash”，就可以在 Hook 里改写 tools。</p>
     */
    public BeforeLlmRequestContext withTools(List<Map<String, Object>> newTools) {
        return new BeforeLlmRequestContext(
                sessionName,
                runId,
                round,
                model,
                maxContextTokens,
                contextSummary,
                control,
                messages,
                newTools
        );
    }
}
