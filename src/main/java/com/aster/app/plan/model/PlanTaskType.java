package com.aster.app.plan.model;

/**
 * Planner 可以生成的任务类型。
 *
 * <p>/team 只使用 PLANNING、FILE_READ、ANALYSIS；
 * 动态 /plan 可以使用全部类型。</p>
 */
public enum PlanTaskType {
    PLANNING,
    FILE_READ,
    FILE_WRITE,
    COMMAND,
    ANALYSIS,
    VERIFICATION
}
