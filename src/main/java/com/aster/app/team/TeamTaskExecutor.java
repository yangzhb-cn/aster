package com.aster.app.team;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.runtime.AgentEventPublisher;
import com.aster.app.team.model.TeamRole;
import com.aster.core.event.model.AgentEvent;

import java.util.Objects;

/**
 * 用子 Agent 执行固定 DAG 中的单个 Team 节点。
 */
public class TeamTaskExecutor {
    private final TeamAgentFactory agentFactory;
    private final AgentEventPublisher eventPublisher;

    public TeamTaskExecutor(TeamAgentFactory agentFactory, AgentEventPublisher eventPublisher) {
        this.agentFactory = Objects.requireNonNull(agentFactory);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    /**
     * 执行一个 DAG 节点，并发布成员开始/完成事件。
     */
    public String execute(PlanTask task, ExecutionPlan plan) throws Exception {
        TeamRole role = roleFor(task);
        long start = System.nanoTime();
        eventPublisher.publish(new AgentEvent.TeamMemberStarted(
                task.id(),
                role.id(),
                task.description()
        ));
        try {
            String output = agentFactory.run(role, task.id(), memberPrompt(role, task, plan), eventPublisher);
            long elapsedMillis = elapsedMillis(start);
            eventPublisher.publish(new AgentEvent.TeamMemberFinished(
                    task.id(),
                    role.id(),
                    true,
                    output,
                    elapsedMillis
            ));
            return output;
        } catch (Exception e) {
            long elapsedMillis = elapsedMillis(start);
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            eventPublisher.publish(new AgentEvent.TeamMemberFinished(
                    task.id(),
                    role.id(),
                    false,
                    error,
                    elapsedMillis
            ));
            throw e;
        }
    }

    private TeamRole roleFor(PlanTask task) {
        return switch (task.id()) {
            case TeamPlanFactory.PLANNER_ID -> TeamRole.PLANNER;
            case TeamPlanFactory.CODE_RESEARCHER_ID -> TeamRole.CODE_RESEARCHER;
            case TeamPlanFactory.RISK_REVIEWER_ID -> TeamRole.RISK_REVIEWER;
            default -> throw new IllegalArgumentException("未知 Team task: " + task.id());
        };
    }

    private String memberPrompt(TeamRole role, PlanTask task, ExecutionPlan plan) {
        return """
                原始探索目标：
                %s

                当前成员：
                - id: %s
                - role: %s
                - description: %s

                已完成依赖结果：
                %s

                请完成当前成员职责，最终用中文输出结构化结果。
                """.formatted(
                plan.task(),
                task.id(),
                role.id(),
                task.description(),
                dependencyResults(task, plan)
        ).strip();
    }

    private String dependencyResults(PlanTask task, ExecutionPlan plan) {
        if (task.dependencies().isEmpty()) {
            return "无";
        }
        StringBuilder output = new StringBuilder();
        for (String dependencyId : task.dependencies()) {
            PlanTask dependency = plan.task(dependencyId);
            output.append("- ").append(dependencyId).append(": ")
                    .append(dependency == null ? "" : dependency.result())
                    .append('\n');
        }
        return output.toString().stripTrailing();
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }
}
