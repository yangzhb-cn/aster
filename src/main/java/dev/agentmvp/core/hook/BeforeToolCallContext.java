package dev.agentmvp.core.hook;

import dev.agentmvp.llm.model.ToolCall;

/**
 * 工具执行前 Hook 上下文。
 *
 * <p>后续做 HITL、高危工具审批、工具权限控制时，都会基于这个上下文判断
 * 当前工具调用是否允许继续。</p>
 */
public record BeforeToolCallContext(
        String sessionName,
        String runId,
        ToolCall toolCall
) {
}
