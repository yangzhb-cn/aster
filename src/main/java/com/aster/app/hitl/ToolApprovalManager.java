package com.aster.app.hitl;

import com.aster.app.hitl.model.ToolApprovalRequest;
import com.aster.app.hitl.model.ToolApprovalResolution;
import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.hook.BeforeToolCallContext;
import com.aster.core.hook.ToolHookDecision;
import com.aster.llm.model.ToolCall;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HITL 工具审批管理器。
 *
 * <p>它保存当前 runtime 内的待审批工具调用，并让 Agent run 阻塞等待
 * UI、Web 或 IM 入口调用 approve/deny 后继续执行。</p>
 */
public class ToolApprovalManager {
    private final Object lock = new Object();
    private final Map<String, PendingApproval> pending = new LinkedHashMap<>();
    private AgentEventBus eventBus;

    /**
     * 绑定当前 runtime 的事件总线。
     */
    public void attachEventBus(AgentEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * 创建审批请求并阻塞等待用户决策。
     */
    public ToolHookDecision awaitApproval(BeforeToolCallContext context, String reason) throws IOException {
        ToolCall call = context.toolCall();
        ToolApprovalRequest request = new ToolApprovalRequest(
                call.id(),
                context.sessionName(),
                context.runId(),
                call.id(),
                call.function().name(),
                call.function().argumentsJson(),
                reason == null ? "" : reason,
                Instant.now()
        );
        PendingApproval approval = new PendingApproval(request);

        synchronized (lock) {
            pending.put(request.approvalId(), approval);
        }
        publishRequested(request);

        ToolApprovalResolution resolution = waitForResolution(approval);
        synchronized (lock) {
            pending.remove(request.approvalId());
        }
        if (resolution.approved()) {
            return ToolHookDecision.allow();
        }
        return ToolHookDecision.deny("工具调用未获人工审批通过。原因：" + resolution.reason());
    }

    /**
     * 批准单个待审批工具调用。
     */
    public boolean approve(String approvalId) {
        return resolve(approvalId, ToolApprovalResolution.approved("用户审批通过"));
    }

    /**
     * 拒绝单个待审批工具调用。
     */
    public boolean deny(String approvalId, String reason) {
        return resolve(approvalId, ToolApprovalResolution.denied(reason));
    }

    /**
     * 批准当前所有待审批工具调用。
     */
    public int approveAll() {
        return resolveAll(ToolApprovalResolution.approved("用户审批通过全部工具"));
    }

    /**
     * 拒绝当前所有待审批工具调用。
     */
    public int denyAll(String reason) {
        return resolveAll(ToolApprovalResolution.denied(reason));
    }

    /**
     * 取消所有等待中的审批，通常用于 stop 或 runtime 关闭。
     */
    public boolean cancelAll(String reason) {
        return resolveAll(ToolApprovalResolution.denied(reason)) > 0;
    }

    /**
     * 返回当前待审批请求快照。
     */
    public List<ToolApprovalRequest> pendingApprovals() {
        synchronized (lock) {
            return pending.values().stream()
                    .map(PendingApproval::request)
                    .toList();
        }
    }

    private ToolApprovalResolution waitForResolution(PendingApproval approval) throws IOException {
        synchronized (lock) {
            while (approval.resolution() == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("tool approval interrupted", e);
                }
            }
            return approval.resolution();
        }
    }

    private boolean resolve(String approvalId, ToolApprovalResolution resolution) {
        PendingApproval approval;
        synchronized (lock) {
            approval = pending.get(approvalId);
            if (approval == null || approval.resolution() != null) {
                return false;
            }
            approval.resolve(resolution);
            lock.notifyAll();
        }
        publishResolved(approval.request(), resolution);
        return true;
    }

    private int resolveAll(ToolApprovalResolution resolution) {
        List<PendingApproval> approvals = new ArrayList<>();
        synchronized (lock) {
            for (PendingApproval approval : pending.values()) {
                if (approval.resolution() == null) {
                    approval.resolve(resolution);
                    approvals.add(approval);
                }
            }
            if (!approvals.isEmpty()) {
                lock.notifyAll();
            }
        }
        for (PendingApproval approval : approvals) {
            publishResolved(approval.request(), resolution);
        }
        return approvals.size();
    }

    private void publishRequested(ToolApprovalRequest request) {
        AgentEventBus bus = eventBus;
        if (bus != null) {
            bus.publish(new AgentEvent.ToolApprovalRequested(
                    request.approvalId(),
                    request.toolCallId(),
                    request.toolName(),
                    request.argumentsJson(),
                    request.reason()
            ));
        }
    }

    private void publishResolved(ToolApprovalRequest request, ToolApprovalResolution resolution) {
        AgentEventBus bus = eventBus;
        if (bus != null) {
            bus.publish(new AgentEvent.ToolApprovalResolved(
                    request.approvalId(),
                    request.toolCallId(),
                    request.toolName(),
                    resolution.approved(),
                    resolution.reason()
            ));
        }
    }

    private static class PendingApproval {
        private final ToolApprovalRequest request;
        private ToolApprovalResolution resolution;

        private PendingApproval(ToolApprovalRequest request) {
            this.request = request;
        }

        private ToolApprovalRequest request() {
            return request;
        }

        private ToolApprovalResolution resolution() {
            return resolution;
        }

        private void resolve(ToolApprovalResolution resolution) {
            this.resolution = resolution;
        }
    }
}
