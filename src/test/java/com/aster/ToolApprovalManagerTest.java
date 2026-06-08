package com.aster;

import com.aster.app.hitl.ToolApprovalHook;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.hook.BeforeToolCallContext;
import com.aster.core.hook.ToolHookDecision;
import com.aster.core.hook.ToolHookDecisionType;
import com.aster.llm.text.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HITL 工具审批测试。
 */
class ToolApprovalManagerTest {
    /**
     * 验证审批请求会阻塞工具 Hook，并在 approve 后放行。
     */
    @Test
    void waitsForApprovalAndAllowsAfterApprove() throws Exception {
        ToolApprovalManager manager = new ToolApprovalManager();
        List<AgentEvent> events = new ArrayList<>();
        manager.attachEventBus(new AgentEventBus("session", List.of(envelope -> events.add(envelope.event()))));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ToolHookDecision> future = executor.submit(() -> manager.awaitApproval(context("bash"), "需要审批"));

            waitForPending(manager);
            assertEquals(1, manager.pendingApprovals().size());
            assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.ToolApprovalRequested));

            assertTrue(manager.approve("call_1"));
            assertEquals(ToolHookDecisionType.ALLOW, future.get(1, TimeUnit.SECONDS).type());
            assertTrue(events.stream().anyMatch(event ->
                    event instanceof AgentEvent.ToolApprovalResolved resolved && resolved.approved()
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证无 id 的批量拒绝会唤醒等待中的审批。
     */
    @Test
    void denyAllWakesWaitingApproval() throws Exception {
        ToolApprovalManager manager = new ToolApprovalManager();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ToolHookDecision> future = executor.submit(() -> manager.awaitApproval(context("write"), "需要审批"));

            waitForPending(manager);
            assertEquals(1, manager.denyAll("不允许写文件"));

            ToolHookDecision decision = future.get(1, TimeUnit.SECONDS);
            assertEquals(ToolHookDecisionType.DENY, decision.type());
            assertTrue(decision.reason().contains("不允许写文件"));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证默认 Hook 只拦截 bash/write/edit。
     */
    @Test
    void hookAllowsUnprotectedTools() throws Exception {
        ToolApprovalHook hook = new ToolApprovalHook(new ToolApprovalManager());

        ToolHookDecision decision = hook.handle(context("read"));

        assertEquals(ToolHookDecisionType.ALLOW, decision.type());
    }

    /**
     * 验证 bash 伪定时命令会被直接拒绝，不进入人工审批队列。
     */
    @Test
    void hookDeniesBashTimingCommandsBeforeApproval() throws Exception {
        ToolApprovalManager manager = new ToolApprovalManager();
        ToolApprovalHook hook = new ToolApprovalHook(manager);

        ToolHookDecision decision = hook.handle(context(
                "bash",
                "{\"command\":\"sleep 60 && echo done\",\"timeoutSeconds\":90}"
        ));

        assertEquals(ToolHookDecisionType.DENY, decision.type());
        assertTrue(decision.reason().contains("background_task"));
        assertEquals(List.of(), manager.pendingApprovals());
    }

    private BeforeToolCallContext context(String toolName) {
        return context(toolName, "{\"path\":\"README.md\"}");
    }

    private BeforeToolCallContext context(String toolName, String argumentsJson) {
        return new BeforeToolCallContext(
                "session",
                "run_1",
                ToolCall.function("call_1", toolName, argumentsJson)
        );
    }

    private void waitForPending(ToolApprovalManager manager) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            if (!manager.pendingApprovals().isEmpty()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }
}
