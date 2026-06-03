package com.aster.app.plan.model;

/**
 * 一次 DAG 执行的最终结果。
 */
public record PlanRunResult(
        boolean success,
        String failure
) {
}
