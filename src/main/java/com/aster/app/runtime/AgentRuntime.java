package com.aster.app.runtime;

import com.aster.core.agent.AgentLoop;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.hitl.model.ToolApprovalRequest;
import com.aster.app.room.RoomAgentPromptStore;
import com.aster.app.room.RoomAgentRegistry;
import com.aster.app.room.RoomAgentSessionCleaner;
import com.aster.app.room.RoomCoordinator;
import com.aster.app.room.RoomHub;
import com.aster.app.room.RoomMembershipStore;
import com.aster.app.room.RoomStore;
import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMemberView;
import com.aster.app.room.model.RoomMembership;
import com.aster.app.room.model.RoomSendResult;
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
    private final RoomStore roomStore;
    private final RoomHub roomHub;
    private final RoomCoordinator roomCoordinator;
    private final RoomAgentRegistry roomAgentRegistry;
    private final RoomAgentPromptStore roomAgentPromptStore;
    private final RoomMembershipStore roomMembershipStore;
    private final RoomAgentSessionCleaner roomAgentSessionCleaner;
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
            RoomStore roomStore,
            RoomHub roomHub,
            RoomCoordinator roomCoordinator,
            RoomAgentRegistry roomAgentRegistry,
            RoomAgentPromptStore roomAgentPromptStore,
            RoomMembershipStore roomMembershipStore,
            RoomAgentSessionCleaner roomAgentSessionCleaner,
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
        this.roomStore = Objects.requireNonNull(roomStore);
        this.roomHub = Objects.requireNonNull(roomHub);
        this.roomCoordinator = Objects.requireNonNull(roomCoordinator);
        this.roomAgentRegistry = Objects.requireNonNull(roomAgentRegistry);
        this.roomAgentPromptStore = Objects.requireNonNull(roomAgentPromptStore);
        this.roomMembershipStore = Objects.requireNonNull(roomMembershipStore);
        this.roomAgentSessionCleaner = Objects.requireNonNull(roomAgentSessionCleaner);
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
     * 列出 Web 聊天室。
     */
    public List<ChatRoom> listRooms() throws IOException {
        return roomStore.listActive();
    }

    /**
     * 列出已归档聊天室。
     */
    public List<ChatRoom> listArchivedRooms() throws IOException {
        return roomStore.listArchived();
    }

    /**
     * 确保存在默认聊天室。
     */
    public ChatRoom ensureDefaultRoom() throws IOException {
        ChatRoom room = roomStore.ensureDefault();
        roomMembershipStore.ensureRoomMembers(room.roomId(), roomAgentRegistry.listActive());
        return room;
    }

    /**
     * 新建聊天室。
     */
    public ChatRoom createRoom(String name) throws IOException {
        ChatRoom room = roomStore.create(name);
        roomMembershipStore.ensureRoomMembers(room.roomId(), roomAgentRegistry.listActive());
        return room;
    }

    /**
     * 更新聊天室名称和主题。
     */
    public ChatRoom updateRoom(String roomId, String name, String topic) throws IOException {
        return roomStore.update(roomId, name, topic);
    }

    /**
     * 归档聊天室。
     */
    public ChatRoom archiveRoom(String roomId) throws IOException {
        return roomStore.archive(roomId);
    }

    /**
     * 从归档恢复聊天室。
     */
    public ChatRoom restoreRoom(String roomId) throws IOException {
        return roomStore.restore(roomId);
    }

    /**
     * 物理删除聊天室及其消息、私有 Agent session。
     */
    public ChatRoom deleteRoomPermanently(String roomId) throws IOException {
        ChatRoom deleted = roomStore.deletePermanently(roomId);
        roomHub.deleteRoom(roomId);
        roomMembershipStore.deleteRoom(roomId);
        roomAgentSessionCleaner.deleteRoomSessions(roomId);
        return deleted;
    }

    /**
     * 读取聊天室共享消息。
     */
    public List<HubMessage> roomMessages(String roomId) throws IOException {
        return roomCoordinator.messages(roomId);
    }

    /**
     * 发送聊天室消息，并触发被 @ 的 Agent。
     */
    public RoomSendResult sendRoomMessage(String roomId, String text) throws IOException {
        return roomCoordinator.send(roomId, text);
    }

    /**
     * 列出当前聊天室成员。
     */
    public List<RoomMemberView> listRoomMembers(String roomId) throws IOException {
        ensureActiveRoom(roomId);
        return memberViews(roomMembershipStore.ensureRoomMembers(roomId, roomAgentRegistry.listActive()));
    }

    /**
     * 列出当前聊天室已移除成员。
     */
    public List<RoomMemberView> listArchivedRoomMembers(String roomId) throws IOException {
        ensureActiveRoom(roomId);
        return memberViews(roomMembershipStore.listArchived(roomId));
    }

    /**
     * 列出可以加入当前聊天室的全局 Agent。
     */
    public List<RoomAgentProfile> listAvailableRoomAgents(String roomId) throws IOException {
        ensureActiveRoom(roomId);
        List<RoomMembership> active = roomMembershipStore.ensureRoomMembers(roomId, roomAgentRegistry.listActive());
        List<RoomMembership> archived = roomMembershipStore.listArchived(roomId);
        List<String> knownIds = new java.util.ArrayList<>();
        knownIds.addAll(active.stream().map(RoomMembership::agentId).toList());
        knownIds.addAll(archived.stream().map(RoomMembership::agentId).toList());
        return roomAgentRegistry.listActive().stream()
                .filter(agent -> !knownIds.contains(agent.agentId()))
                .toList();
    }

    /**
     * 把全局 Agent 加入当前聊天室。
     */
    public RoomMembership addRoomMember(String roomId, String agentId) throws IOException {
        ensureActiveRoom(roomId);
        ensureActiveRoomAgent(agentId);
        return roomMembershipStore.add(roomId, agentId);
    }

    /**
     * 从当前聊天室移除 Agent。
     */
    public RoomMembership archiveRoomMember(String roomId, String agentId) throws IOException {
        ensureActiveRoom(roomId);
        return roomMembershipStore.archive(roomId, agentId);
    }

    /**
     * 恢复当前聊天室已移除的 Agent。
     */
    public RoomMembership restoreRoomMember(String roomId, String agentId) throws IOException {
        ensureActiveRoom(roomId);
        ensureActiveRoomAgent(agentId);
        return roomMembershipStore.restore(roomId, agentId);
    }

    /**
     * 列出聊天室 Agent。
     */
    public List<RoomAgentProfile> listRoomAgents() throws IOException {
        return roomAgentRegistry.listActive();
    }

    /**
     * 列出已归档聊天室 Agent。
     */
    public List<RoomAgentProfile> listArchivedRoomAgents() throws IOException {
        return roomAgentRegistry.listArchived();
    }

    /**
     * 新增聊天室 Agent。
     */
    public RoomAgentProfile createRoomAgent(RoomAgentInput input) throws IOException {
        return roomAgentRegistry.create(input);
    }

    /**
     * 更新聊天室 Agent。
     */
    public RoomAgentProfile updateRoomAgent(RoomAgentInput input) throws IOException {
        return roomAgentRegistry.update(input);
    }

    /**
     * 归档聊天室 Agent。
     */
    public RoomAgentProfile archiveRoomAgent(String agentId) throws IOException {
        return roomAgentRegistry.archive(agentId);
    }

    /**
     * 从归档恢复聊天室 Agent。
     */
    public RoomAgentProfile restoreRoomAgent(String agentId) throws IOException {
        return roomAgentRegistry.restore(agentId);
    }

    /**
     * 物理删除聊天室 Agent 配置、prompt 和私有 session。
     */
    public RoomAgentProfile deleteRoomAgentPermanently(String agentId) throws IOException {
        RoomAgentProfile deleted = roomAgentRegistry.deletePermanently(agentId);
        roomAgentPromptStore.delete(deleted);
        roomMembershipStore.deleteAgent(agentId);
        roomAgentSessionCleaner.deleteAgentSessions(agentId);
        return deleted;
    }

    /**
     * 读取聊天室 Agent 的外部 system prompt。
     */
    public String roomAgentPrompt(RoomAgentProfile profile) throws IOException {
        return roomAgentPromptStore.read(profile);
    }

    private List<RoomMemberView> memberViews(List<RoomMembership> memberships) throws IOException {
        List<RoomAgentProfile> agents = roomAgentRegistry.listAll();
        java.util.Map<String, RoomAgentProfile> agentById = new java.util.HashMap<>();
        for (RoomAgentProfile agent : agents) {
            agentById.put(agent.agentId(), agent);
        }
        return memberships.stream()
                .map(membership -> {
                    RoomAgentProfile agent = agentById.get(membership.agentId());
                    return agent == null ? null : new RoomMemberView(membership, agent);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private ChatRoom ensureActiveRoom(String roomId) throws IOException {
        return roomStore.get(roomId)
                .filter(room -> !room.archived())
                .orElseThrow(() -> new IOException("room not found: " + roomId));
    }

    private RoomAgentProfile ensureActiveRoomAgent(String agentId) throws IOException {
        return roomAgentRegistry.get(agentId)
                .filter(agent -> !agent.archived())
                .orElseThrow(() -> new IOException("room agent not found: " + agentId));
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
