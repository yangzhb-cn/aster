package dev.agentmvp.hook;

/**
 * 工具调用 Hook 决策。
 *
 * <p>当前 AgentLoop 已支持 ALLOW 和 DENY。
 * PAUSE_FOR_APPROVAL 是给后续 HITL 预留的语义，接入 TUI 审批前先不会使用。</p>
 */
public record ToolHookDecision(
        ToolHookDecisionType type,
        String reason
) {
    /**
     * 允许执行工具。
     */
    public static ToolHookDecision allow() {
        return new ToolHookDecision(ToolHookDecisionType.ALLOW, "");
    }

    /**
     * 拒绝执行工具。
     */
    public static ToolHookDecision deny(String reason) {
        return new ToolHookDecision(ToolHookDecisionType.DENY, reason);
    }

    /**
     * 需要人工审批。
     */
    public static ToolHookDecision pauseForApproval(String reason) {
        return new ToolHookDecision(ToolHookDecisionType.PAUSE_FOR_APPROVAL, reason);
    }
}
