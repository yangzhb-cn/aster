package com.aster.ui.web;

/**
 * Web 会话运行状态。
 *
 * <p>Web 可以同时保留多个 session 的 AgentRuntime。这个 DTO 用来把每个
 * session 的运行态压成前端左侧列表需要的最小信息。</p>
 */
public record WebSessionRuntimeStatus(
        String sessionId,
        boolean active,
        boolean busy,
        boolean pendingPlan,
        int queuedCount,
        int pendingApprovalCount
) {
}
