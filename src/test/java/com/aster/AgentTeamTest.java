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

        assertEquals(3, plan.tasks().size());
        assertEquals(PlanTaskType.PLANNING, plan.task("T1").type());
        assertEquals(List.of("T1"), plan.task("T2").dependencies());
        assertEquals(List.of("T1"), plan.task("T3").dependencies());
    }

    /**
     * 验证 PlanRunner 会先执行 planner，再执行依赖 planner 的两个节点。
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
        }, 2);

        assertTrue(runner.run(plan).success());
        assertEquals(PlanTaskStatus.COMPLETED, plan.task("T1").status());
        assertEquals(PlanTaskStatus.COMPLETED, plan.task("T2").status());
        assertEquals(PlanTaskStatus.COMPLETED, plan.task("T3").status());
        assertEquals("T1", executed.get(0));
        assertTrue(executed.contains("T2"));
        assertTrue(executed.contains("T3"));
    }
}
