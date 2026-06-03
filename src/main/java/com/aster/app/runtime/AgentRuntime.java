package com.aster.app.runtime;

import com.aster.core.agent.AgentLoop;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.hitl.model.ToolApprovalRequest;
import com.aster.app.team.AgentTeamRunner;
import com.aster.app.team.model.TeamRunOutput;
import com.aster.core.event.model.AgentEvent;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Agent 运行时对象。
 *
 * <p>它把 AgentLoop 和需要关闭的执行器放在一起。
 * 这样 TUI、Web 或未来其他入口不需要知道底层有多少组件需要释放。</p>
 */
public class AgentRuntime implements AutoCloseable {
    private final AgentLoop agentLoop;
    private final AgentRunCoordinator runCoordinator;
    private final AgentTeamRunner agentTeamRunner;
    private final PlanModeCoordinator planModeCoordinator;
    private final AgentEventPublisher eventPublisher;
    private final BackgroundTaskManager backgroundTaskManager;
    private final ToolApprovalManager toolApprovalManager;
    private final ParallelToolExecutor parallelToolExecutor;
    private final McpToolExecutor mcpToolExecutor;
    private final OpenAiCompatibleProvider provider;
    private final String sessionName;
    private final String teamFinalSummaryUserPrompt;
    private final int skillCount;
    private final ExecutorService teamExecutor = Executors.newSingleThreadExecutor();
    private final Object teamLock = new Object();

    private boolean teamBusy;
    private Future<?> currentTeam;

    public AgentRuntime(
            AgentLoop agentLoop,
            AgentRunCoordinator runCoordinator,
            AgentTeamRunner agentTeamRunner,
            PlanModeCoordinator planModeCoordinator,
            AgentEventPublisher eventPublisher,
            BackgroundTaskManager backgroundTaskManager,
            ToolApprovalManager toolApprovalManager,
            ParallelToolExecutor parallelToolExecutor,
            McpToolExecutor mcpToolExecutor,
            OpenAiCompatibleProvider provider,
            String sessionName,
            String teamFinalSummaryUserPrompt,
            int skillCount
    ) {
        this.agentLoop = Objects.requireNonNull(agentLoop);
        this.runCoordinator = Objects.requireNonNull(runCoordinator);
        this.agentTeamRunner = Objects.requireNonNull(agentTeamRunner);
        this.planModeCoordinator = Objects.requireNonNull(planModeCoordinator);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager);
        this.toolApprovalManager = Objects.requireNonNull(toolApprovalManager);
        this.parallelToolExecutor = Objects.requireNonNull(parallelToolExecutor);
        this.mcpToolExecutor = Objects.requireNonNull(mcpToolExecutor);
        this.provider = Objects.requireNonNull(provider);
        this.sessionName = Objects.requireNonNull(sessionName);
        this.teamFinalSummaryUserPrompt = Objects.requireNonNull(teamFinalSummaryUserPrompt);
        this.skillCount = skillCount;
    }

    /**
     * 执行一次用户请求。
     */
    public String run(String userInput) throws IOException {
        return agentLoop.run(userInput);
    }

    /**
     * 提交用户输入。运行中提交会进入 follow-up 队列。
     */
    public void submit(String userInput) {
        if (teamBusy()) {
            throw new IllegalStateException("Agent Team 正在运行，结束后再提交普通消息。");
        }
        if (planModeCoordinator.isBusy()) {
            throw new IllegalStateException("Plan 正在生成或执行，结束后再提交普通消息。");
        }
        if (planModeCoordinator.hasPendingPlan()) {
            throw new IllegalStateException("当前有待执行计划，请输入 /start 执行，/plan <新目标> 重新计划，或 /plan cancel 取消。");
        }
        runCoordinator.submit(userInput);
    }

    /**
     * 启动一次固定 DAG 的 Agent Team 探索。
     */
    public void submitTeam(String task) {
        String input = requireText(task, "task");
        synchronized (teamLock) {
            if (teamBusy) {
                throw new IllegalStateException("Agent Team 正在运行。");
            }
            if (planModeCoordinator.isBusy() || planModeCoordinator.hasPendingPlan()) {
                throw new IllegalStateException("Plan 模式中不能启动 /team，请先 /start 或 /plan cancel。");
            }
            if (runCoordinator.isBusy()) {
                throw new IllegalStateException("Agent 正在运行，结束后再启动 /team。");
            }
            teamBusy = true;
            currentTeam = teamExecutor.submit(() -> runTeam(input));
        }
    }

    /**
     * 生成一份动态 DAG 计划，等待用户 /start 确认执行。
     */
    public void submitPlan(String task) {
        if (teamBusy()) {
            throw new IllegalStateException("Agent Team 正在运行，结束后再进入 /plan。");
        }
        planModeCoordinator.submitPlan(task);
    }

    /**
     * 执行当前待确认计划。
     */
    public boolean startPlan() {
        if (teamBusy()) {
            throw new IllegalStateException("Agent Team 正在运行。");
        }
        return planModeCoordinator.startPlan();
    }

    /**
     * 取消当前 Plan。
     */
    public boolean cancelPlan() {
        return planModeCoordinator.cancelPlan("用户取消 Plan。");
    }

    /**
     * 向当前 run 发送运行中引导。
     */
    public boolean steer(String text) {
        return runCoordinator.steer(text);
    }

    /**
     * 请求当前 run 停止。
     */
    public boolean stop() {
        boolean stopped = runCoordinator.stop();
        boolean canceledApprovals = toolApprovalManager.cancelAll("用户请求停止");
        boolean stoppedTeam = stopTeam();
        boolean stoppedPlan = planModeCoordinator.cancelPlan("用户请求停止。");
        return stopped || canceledApprovals || stoppedTeam || stoppedPlan;
    }

    /**
     * 批准一个待审批工具调用。
     */
    public boolean approveTool(String approvalId) {
        return toolApprovalManager.approve(approvalId);
    }

    /**
     * 拒绝一个待审批工具调用。
     */
    public boolean denyTool(String approvalId, String reason) {
        return toolApprovalManager.deny(approvalId, reason);
    }

    /**
     * 批准当前全部待审批工具调用。
     */
    public int approveAllTools() {
        return toolApprovalManager.approveAll();
    }

    /**
     * 拒绝当前全部待审批工具调用。
     */
    public int denyAllTools(String reason) {
        return toolApprovalManager.denyAll(reason);
    }

    /**
     * 当前待审批工具调用列表。
     */
    public List<ToolApprovalRequest> pendingToolApprovals() {
        return toolApprovalManager.pendingApprovals();
    }

    /**
     * 判断当前是否有正在执行或等待执行的输入。
     */
    public boolean isBusy() {
        return runCoordinator.isBusy() || teamBusy() || planModeCoordinator.isBusy();
    }

    /**
     * 判断当前是否有等待 /start 的计划。
     */
    public boolean hasPendingPlan() {
        return planModeCoordinator.hasPendingPlan();
    }

    /**
     * 返回 follow-up 队列长度。
     */
    public int queuedCount() {
        return runCoordinator.queuedCount();
    }

    /**
     * 后台任务管理器。
     */
    public BackgroundTaskManager backgroundTaskManager() {
        return backgroundTaskManager;
    }

    /**
     * 当前模型供应商信息。
     */
    public OpenAiCompatibleProvider provider() {
        return provider;
    }

    /**
     * 启动时扫描到的 Skill 数量。
     */
    public int skillCount() {
        return skillCount;
    }

    /**
     * 当前绑定的 session 名称。
     */
    public String sessionName() {
        return sessionName;
    }

    /**
     * 关闭运行时资源。
     */
    @Override
    public void close() {
        toolApprovalManager.cancelAll("runtime closing");
        planModeCoordinator.close();
        teamExecutor.shutdownNow();
        runCoordinator.close();
        backgroundTaskManager.close();
        parallelToolExecutor.close();
        mcpToolExecutor.close();
    }

    private void runTeam(String task) {
        eventPublisher.beginRun();
        TeamRunOutput output = null;
        try {
            output = agentTeamRunner.run(task);
        } finally {
            synchronized (teamLock) {
                teamBusy = false;
                currentTeam = null;
            }
        }
        if (output != null && !Thread.currentThread().isInterrupted()) {
            runCoordinator.submit(renderTeamFinalSummaryPrompt(output));
        }
    }

    private boolean stopTeam() {
        synchronized (teamLock) {
            if (!teamBusy || currentTeam == null) {
                return false;
            }
            currentTeam.cancel(true);
            teamBusy = false;
            currentTeam = null;
        }
        eventPublisher.publish(new AgentEvent.RunStopped("Agent Team 已请求停止。"));
        return true;
    }

    private boolean teamBusy() {
        synchronized (teamLock) {
            return teamBusy;
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    /**
     * 把 Team 完整探索材料包装成主 Agent 的内部整理请求。
     */
    private String renderTeamFinalSummaryPrompt(TeamRunOutput output) {
        return teamFinalSummaryUserPrompt
                .replace("{{task}}", output.task())
                .replace("{{team_material}}", output.material());
    }
}
