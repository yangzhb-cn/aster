package com.aster.app.runtime;

import com.aster.app.plan.PlanPlannerAgent;
import com.aster.app.plan.PlanRenderer;
import com.aster.app.plan.PlanRunner;
import com.aster.app.plan.PlanTaskExecutor;
import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanRunResult;
import com.aster.core.event.model.AgentEvent;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * /plan 模式协调器。
 *
 * <p>它保存待确认的动态 DAG，并在用户输入 /start 后交给 PlanRunner 解依赖执行。
 * 执行材料最后会作为内部请求交给主 Agent 整理。</p>
 */
public class PlanModeCoordinator implements AutoCloseable {
    private static final int PARALLELISM = 5;

    private final PlanPlannerAgent plannerAgent;
    private final PlanRunner planRunner;
    private final AgentRunCoordinator runCoordinator;
    private final AgentEventPublisher eventPublisher;
    private final String finalSummaryUserPrompt;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();

    private PendingPlan pendingPlan;
    private boolean busy;
    private Future<?> current;
    private long version;

    public PlanModeCoordinator(
            PlanPlannerAgent plannerAgent,
            PlanTaskExecutor taskExecutor,
            AgentRunCoordinator runCoordinator,
            AgentEventPublisher eventPublisher,
            String finalSummaryUserPrompt
    ) {
        this.plannerAgent = Objects.requireNonNull(plannerAgent);
        this.planRunner = new PlanRunner(Objects.requireNonNull(taskExecutor)::execute, PARALLELISM);
        this.runCoordinator = Objects.requireNonNull(runCoordinator);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.finalSummaryUserPrompt = Objects.requireNonNull(finalSummaryUserPrompt);
    }

    /**
     * 生成或重新生成一份待确认的动态 DAG。
     */
    public void submitPlan(String task) {
        String input = requireText(task, "task");
        synchronized (lock) {
            if (busy) {
                throw new IllegalStateException("Plan 正在生成或执行。");
            }
            if (runCoordinator.isBusy()) {
                throw new IllegalStateException("Agent 正在运行，结束后再进入 /plan。");
            }
            pendingPlan = null;
            busy = true;
            long runVersion = ++version;
            current = executor.submit(() -> draftPlan(input, runVersion));
        }
    }

    /**
     * 执行当前待确认计划；没有计划时返回 false。
     */
    public boolean startPlan() {
        PendingPlan plan;
        synchronized (lock) {
            if (busy) {
                throw new IllegalStateException("Plan 正在生成或执行。");
            }
            if (pendingPlan == null) {
                return false;
            }
            plan = pendingPlan;
            pendingPlan = null;
            busy = true;
            long runVersion = ++version;
            current = executor.submit(() -> executePlan(plan, runVersion));
        }
        return true;
    }

    /**
     * 取消待确认或正在运行的 Plan。
     */
    public boolean cancelPlan(String reason) {
        Future<?> future;
        boolean canceled;
        synchronized (lock) {
            canceled = pendingPlan != null || busy;
            pendingPlan = null;
            future = current;
            current = null;
            busy = false;
            version++;
        }
        if (future != null) {
            future.cancel(true);
        }
        if (canceled) {
            eventPublisher.publish(new AgentEvent.PlanCanceled(reason));
        }
        return canceled;
    }

    /**
     * 判断 Plan 是否正在生成或执行。
     */
    public boolean isBusy() {
        synchronized (lock) {
            return busy;
        }
    }

    /**
     * 判断是否存在等待 /start 的计划。
     */
    public boolean hasPendingPlan() {
        synchronized (lock) {
            return pendingPlan != null;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void draftPlan(String task, long runVersion) {
        eventPublisher.beginRun();
        eventPublisher.publish(new AgentEvent.PlanDraftStarted(task));
        try {
            ExecutionPlan plan = plannerAgent.createPlan(task);
            String markdown = PlanRenderer.renderPlan(plan);
            synchronized (lock) {
                if (runVersion != version || Thread.currentThread().isInterrupted()) {
                    return;
                }
                pendingPlan = new PendingPlan(task, plan, markdown);
                busy = false;
                current = null;
            }
            eventPublisher.publish(new AgentEvent.PlanProposed(task, markdown));
        } catch (Exception e) {
            if (stale(runVersion)) {
                return;
            }
            clearBusy();
            eventPublisher.publish(new AgentEvent.PlanFailed(task, message(e)));
        }
    }

    private void executePlan(PendingPlan pending, long runVersion) {
        eventPublisher.beginRun();
        eventPublisher.publish(new AgentEvent.PlanExecutionStarted(pending.task(), pending.markdown()));
        PlanRunResult result;
        try {
            result = planRunner.run(pending.plan());
        } catch (Exception e) {
            result = new PlanRunResult(false, message(e));
        }

        if (stale(runVersion)) {
            return;
        }
        String material = PlanRenderer.renderExecutionMaterial(pending.plan(), result);
        clearBusy();
        eventPublisher.publish(new AgentEvent.PlanExecutionFinished(result.success(), material));
        if (!Thread.currentThread().isInterrupted()) {
            runCoordinator.submit(renderFinalSummaryPrompt(pending.task(), material));
        }
    }

    private void clearBusy() {
        synchronized (lock) {
            busy = false;
            current = null;
        }
    }

    private boolean stale(long runVersion) {
        synchronized (lock) {
            return runVersion != version || Thread.currentThread().isInterrupted();
        }
    }

    private String renderFinalSummaryPrompt(String task, String material) {
        return finalSummaryUserPrompt
                .replace("{{task}}", task)
                .replace("{{plan_result}}", material);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private record PendingPlan(String task, ExecutionPlan plan, String markdown) {
    }
}
