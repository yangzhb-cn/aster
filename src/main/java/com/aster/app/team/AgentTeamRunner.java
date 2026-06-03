package com.aster.app.team;

import com.aster.app.plan.PlanRunner;
import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanRunResult;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.runtime.AgentEventPublisher;
import com.aster.core.event.model.AgentEvent;

import java.util.Objects;

/**
 * 固定 DAG 的探索型 Agent Team。
 *
 * <p>第一版只做探索：planner 先运行，code_researcher 和 risk_reviewer
 * 在 planner 完成后并行运行，最终把所有成员结果汇总成一段文本。</p>
 */
public class AgentTeamRunner {
    private static final int PARALLELISM = 2;

    private final AgentEventPublisher eventPublisher;
    private final PlanRunner planRunner;

    public AgentTeamRunner(AgentEventPublisher eventPublisher, TeamTaskExecutor taskExecutor) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.planRunner = new PlanRunner(taskExecutor::execute, PARALLELISM);
    }

    /**
     * 运行一次探索 Team。
     */
    public void run(String task) {
        String input = requireTask(task);
        long start = System.nanoTime();
        ExecutionPlan plan = TeamPlanFactory.explorationPlan(input);
        eventPublisher.publish(new AgentEvent.TeamRunStarted(input, "explore"));
        PlanRunResult result;
        try {
            result = planRunner.run(plan);
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            eventPublisher.publish(new AgentEvent.TeamRunFinished(
                    false,
                    "Agent Team 执行失败：" + error,
                    elapsedMillis(start)
            ));
            return;
        }

        eventPublisher.publish(new AgentEvent.TeamRunFinished(
                result.success(),
                renderSummary(plan, result),
                elapsedMillis(start)
        ));
    }

    private String renderSummary(ExecutionPlan plan, PlanRunResult result) {
        StringBuilder output = new StringBuilder();
        output.append("Agent Team 探索完成：").append(plan.task()).append("\n\n");
        for (PlanTask task : plan.tasks()) {
            output.append("## ").append(task.id()).append(" ").append(task.type()).append("\n");
            output.append(task.status()).append("\n\n");
            output.append(task.result()).append("\n\n");
        }
        if (!result.success()) {
            output.append("失败原因：").append(result.failure()).append('\n');
        }
        return output.toString().stripTrailing();
    }

    private String requireTask(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("team task is required");
        }
        return task.trim();
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }
}
