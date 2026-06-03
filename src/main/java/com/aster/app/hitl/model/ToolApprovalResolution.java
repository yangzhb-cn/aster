package com.aster.app.hitl.model;

/**
 * 人工审批的最终结果。
 */
public record ToolApprovalResolution(
        boolean approved,
        String reason
) {
    /**
     * 创建批准结果。
     */
    public static ToolApprovalResolution approved(String reason) {
        return new ToolApprovalResolution(true, normalizeReason(reason, "用户审批通过"));
    }

    /**
     * 创建拒绝结果。
     */
    public static ToolApprovalResolution denied(String reason) {
        return new ToolApprovalResolution(false, normalizeReason(reason, "用户拒绝执行"));
    }

    private static String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }
}
