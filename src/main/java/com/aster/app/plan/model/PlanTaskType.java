package com.aster.app.plan.model;

/**
 * Planner 可以生成的任务类型。
 *
 * <p>当前 /team 只使用 PLANNING、FILE_READ、ANALYSIS。
 * FILE_WRITE、COMMAND 和 VERIFICATION 先保留给后续 /plan 使用。</p>
 */
public enum PlanTaskType {
    PLANNING,
    FILE_READ,
    FILE_WRITE,
    COMMAND,
    ANALYSIS,
    VERIFICATION
}
