package com.aster.app.hitl.model;

import java.time.Instant;

/**
 * 待人工审批的工具调用请求。
 *
 * <p>approvalId 当前直接使用 toolCallId，方便用户在 TUI、Web、Telegram
 * 里用同一个 id 批准或拒绝。</p>
 */
public record ToolApprovalRequest(
        String approvalId,
        String sessionName,
        String runId,
        String toolCallId,
        String toolName,
        String argumentsJson,
        String reason,
        Instant requestedAt
) {
}
