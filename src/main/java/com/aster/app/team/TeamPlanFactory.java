package com.aster.app.team;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.plan.model.PlanTaskType;

import java.util.List;

/**
 * 创建固定探索 Team DAG。
 *
 * <p>第一版不让 LLM 动态生成 DAG，只使用固定结构：
 * planner 先跑，随后 3 个 code_researcher 和 2 个 risk_reviewer
 * 依赖 planner 并行探索不同代码面和风险面。</p>
 */
public final class TeamPlanFactory {
    public static final String PLANNER_ID = "T1";
    public static final String CODE_RESEARCHER_ID = "T2";
    public static final List<String> CODE_RESEARCHER_IDS = List.of("T2", "T3", "T4");
    public static final String RISK_REVIEWER_ID = "T5";
    public static final List<String> RISK_REVIEWER_IDS = List.of("T5", "T6");

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
                        "阅读 core 主流程和上下文代码，重点看 agent、stage、context、session 的调用链和证据。",
                        PlanTaskType.FILE_READ,
                        List.of(PLANNER_ID)
                ),
                new PlanTask(
                        "T3",
                        "阅读工具和扩展代码，重点看 tool、hook、event、extension、hitl 的注册与执行路径。",
                        PlanTaskType.FILE_READ,
                        List.of(PLANNER_ID)
                ),
                new PlanTask(
                        "T4",
                        "阅读 app 运行时、UI、LLM、prompt 和测试代码，重点看 runtime 装配、tui/web/telegram、模型适配与验证覆盖。",
                        PlanTaskType.FILE_READ,
                        List.of(PLANNER_ID)
                ),
                new PlanTask(
                        RISK_REVIEWER_ID,
                        "独立审查架构和并发风险，重点看分层边界、状态一致性、DAG 调度、事件顺序和停止/失败路径。",
                        PlanTaskType.ANALYSIS,
                        List.of(PLANNER_ID)
                ),
                new PlanTask(
                        "T6",
                        "独立审查产品和验证风险，重点看 UI 展示、IM/Web/TUI 一致性、测试缺口、prompt 误导和可运维性。",
                        PlanTaskType.ANALYSIS,
                        List.of(PLANNER_ID)
                )
        ));
    }
}
