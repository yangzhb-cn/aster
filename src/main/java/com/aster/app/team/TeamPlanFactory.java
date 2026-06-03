package com.aster.app.team;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.plan.model.PlanTaskType;

import java.util.List;

/**
 * 创建固定探索 Team DAG。
 *
 * <p>第一版不让 LLM 动态生成 DAG，只使用固定结构：
 * planner 先跑，code_researcher 和 risk_reviewer 依赖 planner 并行执行。</p>
 */
public final class TeamPlanFactory {
    public static final String PLANNER_ID = "T1";
    public static final String CODE_RESEARCHER_ID = "T2";
    public static final String RISK_REVIEWER_ID = "T3";

    private TeamPlanFactory() {
    }

    /**
     * 为一次探索任务创建固定 DAG。
     */
    public static ExecutionPlan explorationPlan(String task) {
        return new ExecutionPlan(task, List.of(
                new PlanTask(
                        PLANNER_ID,
                        "拆解探索目标，列出需要查看的模块、文件和关键问题。",
                        PlanTaskType.PLANNING,
                        List.of()
                ),
                new PlanTask(
                        CODE_RESEARCHER_ID,
                        "根据 planner 的结果阅读项目代码，整理关键文件、调用链和证据。",
                        PlanTaskType.FILE_READ,
                        List.of(PLANNER_ID)
                ),
                new PlanTask(
                        RISK_REVIEWER_ID,
                        "根据 planner 的结果审查实现风险、边界条件、缺失验证和后续改动面。",
                        PlanTaskType.ANALYSIS,
                        List.of(PLANNER_ID)
                )
        ));
    }
}
