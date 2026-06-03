package com.aster;

import com.aster.app.plan.PlanRunner;
import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTaskStatus;
import com.aster.app.plan.model.PlanTaskType;
import com.aster.app.team.TeamPlanFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent Team 固定 DAG 测试。
 */
class AgentTeamTest {
    /**
     * 验证固定探索 DAG 的依赖关系。
     */
    @Test
    void createsFixedExplorationDag() {
        ExecutionPlan plan = TeamPlanFactory.explorationPlan("探索 Web 入口");

        assertEquals(6, plan.tasks().size());
        assertEquals(PlanTaskType.PLANNING, plan.task("T1").type());
        for (String taskId : TeamPlanFactory.CODE_RESEARCHER_IDS) {
            assertEquals(PlanTaskType.FILE_READ, plan.task(taskId).type());
            assertEquals(List.of("T1"), plan.task(taskId).dependencies());
        }
        for (String taskId : TeamPlanFactory.RISK_REVIEWER_IDS) {
            assertEquals(PlanTaskType.ANALYSIS, plan.task(taskId).type());
            assertEquals(List.of("T1"), plan.task(taskId).dependencies());
        }
    }

    /**
     * 验证 PlanRunner 会先执行 planner，再并行执行 3 个 reader 和 2 个 reviewer。
     */
    @Test
    void runsReadyTasksByDependency() throws Exception {
        ExecutionPlan plan = TeamPlanFactory.explorationPlan("探索运行时");
        List<String> executed = new ArrayList<>();
        PlanRunner runner = new PlanRunner((task, currentPlan) -> {
            synchronized (executed) {
                executed.add(task.id());
            }
            return "done " + task.id();
        }, 5);

        assertTrue(runner.run(plan).success());
        for (String taskId : List.of("T1", "T2", "T3", "T4", "T5", "T6")) {
            assertEquals(PlanTaskStatus.COMPLETED, plan.task(taskId).status());
        }
        assertEquals("T1", executed.get(0));
        for (String taskId : TeamPlanFactory.CODE_RESEARCHER_IDS) {
            assertTrue(executed.indexOf(taskId) > 0);
        }
        for (String taskId : TeamPlanFactory.RISK_REVIEWER_IDS) {
            assertTrue(executed.indexOf(taskId) > 0);
        }
    }
}
