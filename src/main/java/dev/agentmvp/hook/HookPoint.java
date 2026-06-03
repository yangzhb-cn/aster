package dev.agentmvp.hook;

import java.util.Objects;

/**
 * Hook 生命周期点。
 *
 * <p>HookPoint 是 Agent 主流程里的稳定插槽，例如 before_llm_request、
 * before_tool_call、before_tool_result_append。后续新增扩展逻辑时，
 * 不需要改 AgentLoop，只需要把 HookHandler 注册到对应 HookPoint。</p>
 */
public record HookPoint<C, R>(String name) {
    public HookPoint {
        Objects.requireNonNull(name);
        if (name.isBlank()) {
            throw new IllegalArgumentException("HookPoint name must not be blank");
        }
    }
}
