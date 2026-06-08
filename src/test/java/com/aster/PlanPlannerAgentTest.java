package com.aster;

import com.aster.app.plan.PlanPlannerAgent;
import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTaskType;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.ProviderStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 动态 Plan DAG 解析和依赖校验测试。
 */
class PlanPlannerAgentTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证普通 JSON 可以解析为 ExecutionPlan。
     */
    @Test
    void parsesValidPlanJson() throws Exception {
        ExecutionPlan plan = PlanPlannerAgent.parsePlan(objectMapper, "fallback", """
                {
                  "task": "实现 /plan",
                  "tasks": [
                    {"id":"T1","description":"读取现有入口","type":"FILE_READ","dependencies":[]},
                    {"id":"T2","description":"修改 runtime","type":"FILE_WRITE","dependencies":["T1"]},
                    {"id":"T3","description":"运行测试","type":"VERIFICATION","dependencies":["T2"]}
                  ]
                }
                """);

        assertEquals("实现 /plan", plan.task());
        assertEquals(3, plan.tasks().size());
        assertEquals(PlanTaskType.FILE_WRITE, plan.task("T2").type());
        assertEquals("T1", plan.task("T2").dependencies().getFirst());
    }

    /**
     * 验证模型包了代码块时仍能提取 JSON。
     */
    @Test
    void parsesFencedJson() throws Exception {
        ExecutionPlan plan = PlanPlannerAgent.parsePlan(objectMapper, "fallback task", """
                ```json
                {
                  "tasks": [
                    {"id":"T1","description":"分析需求","type":"ANALYSIS","dependencies":[]}
                  ]
                }
                ```
                """);

        assertEquals("fallback task", plan.task());
        assertEquals(PlanTaskType.ANALYSIS, plan.task("T1").type());
    }

    /**
     * 验证未知依赖会被拒绝，避免执行阶段悬空等待。
     */
    @Test
    void rejectsUnknownDependency() {
        assertThrows(IllegalArgumentException.class, () -> PlanPlannerAgent.parsePlan(objectMapper, "task", """
                {
                  "tasks": [
                    {"id":"T1","description":"修改文件","type":"FILE_WRITE","dependencies":["T0"]}
                  ]
                }
                """));
    }

    /**
     * 验证循环依赖会被拒绝。
     */
    @Test
    void rejectsDependencyCycle() {
        assertThrows(IllegalArgumentException.class, () -> PlanPlannerAgent.parsePlan(objectMapper, "task", """
                {
                  "tasks": [
                    {"id":"T1","description":"A","type":"ANALYSIS","dependencies":["T2"]},
                    {"id":"T2","description":"B","type":"ANALYSIS","dependencies":["T1"]}
                  ]
                }
                """));
    }

    /**
     * 验证 Plan planner 使用装配时指定的固定模型。
     */
    @Test
    void createPlanUsesConfiguredModel() throws Exception {
        CapturingClient client = new CapturingClient("""
                {
                  "tasks": [
                    {"id":"T1","description":"分析需求","type":"ANALYSIS","dependencies":[]}
                  ]
                }
                """);
        PlanPlannerAgent agent = new PlanPlannerAgent(objectMapper, client, "system", "deepseek-v4-pro");

        ExecutionPlan plan = agent.createPlan("实现模型路由");

        assertEquals("deepseek-v4-pro", client.request.model());
        assertEquals("实现模型路由", plan.task());
    }

    private static final class CapturingClient implements StreamingChatClient {
        private final String text;
        private ChatRequest request;

        private CapturingClient(String text) {
            this.text = text;
        }

        @Override
        public void stream(ChatRequest request, StreamHandler handler) throws IOException {
            this.request = request;
            handler.onEvent(new ProviderStreamEvent.TextDelta(text));
            handler.onDone();
        }
    }
}
