package com.aster.app.team;

import com.aster.app.plan.PlanRunner;
import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanRunResult;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.runtime.AgentEventPublisher;
import com.aster.app.team.model.TeamRunOutput;
import com.aster.core.event.model.AgentEvent;

import java.util.Objects;

/**
 * 固定 DAG 的探索型 Agent Team。
 *
 * <p>第一版只做探索：planner 先运行，随后 3 个 code_researcher
 * 和 2 个 risk_reviewer 并行探索，最终把完整探索材料交给主 Agent 整理。</p>
 */
public class AgentTeamRunner {
    private static final int PARALLELISM = 5;

    private final AgentEventPublisher eventPublisher;
    private final TeamTaskExecutor taskExecutor;

    public AgentTeamRunner(AgentEventPublisher eventPublisher, TeamTaskExecutor taskExecutor) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
    }

    /**
     * 运行一次探索 Team。
     */
    public TeamRunOutput run(String task, String model) {
        String input = requireTask(task);
        String selectedModel = requireModel(model);
        long start = System.nanoTime();
        ExecutionPlan plan = TeamPlanFactory.explorationPlan(input);
        eventPublisher.publish(new AgentEvent.TeamRunStarted(input, "explore"));
        PlanRunResult result;
        try {
            PlanRunner planRunner = new PlanRunner(
                    (teamTask, currentPlan) -> taskExecutor.execute(teamTask, currentPlan, selectedModel),
                    PARALLELISM
            );
            result = planRunner.run(plan);
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            long elapsedMillis = elapsedMillis(start);
            String material = "Agent Team 执行失败：" + error;
            eventPublisher.publish(new AgentEvent.TeamRunFinished(
                    false,
                    "Agent Team 探索失败，正在交给主 Agent 整理已有材料：" + error,
                    elapsedMillis
            ));
            return new TeamRunOutput(input, false, material, error, elapsedMillis);
        }

        long elapsedMillis = elapsedMillis(start);
        String material = renderMaterial(plan, result);
        eventPublisher.publish(new AgentEvent.TeamRunFinished(
                result.success(),
                result.success()
                        ? "Agent Team 探索完成，正在交给主 Agent 整理。"
                        : "Agent Team 探索失败，正在交给主 Agent 整理已有材料：" + result.failure(),
                elapsedMillis
        ));
        return new TeamRunOutput(input, result.success(), material, result.failure(), elapsedMillis);
    }

    private String renderMaterial(ExecutionPlan plan, PlanRunResult result) {
        StringBuilder output = new StringBuilder();
        output.append("# Agent Team 完整探索材料\n\n");
        output.append("原始探索目标：").append(plan.task()).append("\n\n");
        for (PlanTask task : plan.tasks()) {
            output.append("## ").append(task.id()).append(" ").append(task.type()).append("\n");
            output.append("- 描述：").append(task.description()).append('\n');
            output.append("- 依赖：").append(task.dependencies().isEmpty() ? "无" : String.join(", ", task.dependencies())).append('\n');
            output.append("- 状态：").append(task.status()).append('\n');
            output.append("- 尝试次数：").append(task.attempts()).append("\n\n");
            output.append("成员完整输出：\n");
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

    private String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("team model is required");
        }
        return model.trim();
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }
}
