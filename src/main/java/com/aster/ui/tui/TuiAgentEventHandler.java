package com.aster.ui.tui;

import com.aster.core.event.AgentEventHandler;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;

import java.util.Objects;

/**
 * 把 Agent 事件渲染到 TUI。
 *
 * <p>AgentLoop 不知道自己运行在 CLI、TUI 还是 Web 中。
 * 它只发事件；这里负责把事件转成界面更新。</p>
 */
public class TuiAgentEventHandler implements AgentEventHandler {
    private final AgentTuiWindow window;

    public TuiAgentEventHandler(AgentTuiWindow window) {
        this.window = Objects.requireNonNull(window);
    }

    /**
     * 处理一次 Agent 事件。
     */
    @Override
    public void onEvent(AgentEventEnvelope envelope) {
        AgentEvent event = envelope.event();
        if (event instanceof AgentEvent.RunStarted) {
            window.showRunStarted();
            return;
        }
        if (event instanceof AgentEvent.RunFailed failed) {
            window.showRunFailed(failed.errorMessage());
            return;
        }
        if (event instanceof AgentEvent.RunQueued queued) {
            window.showRunQueued(queued.queueSize());
            return;
        }
        if (event instanceof AgentEvent.SteerReceived steer) {
            window.showSteerReceived(steer.pendingCount());
            return;
        }
        if (event instanceof AgentEvent.StopRequested) {
            window.showStopRequested();
            return;
        }
        if (event instanceof AgentEvent.RunStopped) {
            window.showRunStopped();
            return;
        }
        if (event instanceof AgentEvent.AssistantToken token) {
            window.appendAssistantToken(token.text());
            return;
        }
        if (event instanceof AgentEvent.ReasoningToken token) {
            window.appendReasoningToken(token.text());
            return;
        }
        if (event instanceof AgentEvent.ToolApprovalRequested approval) {
            window.showToolApprovalRequested(
                    approval.approvalId(),
                    approval.toolName(),
                    approval.argumentsJson()
            );
            return;
        }
        if (event instanceof AgentEvent.ToolApprovalResolved approval) {
            window.showToolApprovalResolved(
                    approval.approvalId(),
                    approval.toolName(),
                    approval.approved(),
                    approval.reason()
            );
            return;
        }
        if (event instanceof AgentEvent.TeamRunStarted team) {
            window.showTeamRunStarted(team.task(), team.mode());
            return;
        }
        if (event instanceof AgentEvent.TeamMemberStarted member) {
            window.showTeamMemberStarted(member.taskId(), member.role(), member.description());
            return;
        }
        if (event instanceof AgentEvent.TeamMemberToken token) {
            window.appendTeamMemberToken(token.taskId(), token.role(), token.text());
            return;
        }
        if (event instanceof AgentEvent.TeamMemberFinished member) {
            window.showTeamMemberFinished(
                    member.taskId(),
                    member.role(),
                    member.success(),
                    member.text(),
                    member.elapsedMillis()
            );
            return;
        }
        if (event instanceof AgentEvent.TeamRunFinished team) {
            window.showTeamRunFinished(team.success(), team.summary(), team.elapsedMillis());
            return;
        }
        if (event instanceof AgentEvent.ToolCallStart toolCall) {
            window.showToolStart(toolCall.toolCallId(), toolCall.toolName(), toolCall.argumentsJson());
            return;
        }
        if (event instanceof AgentEvent.ToolCallDone toolResult) {
            window.showToolDone(
                    toolResult.toolCallId(),
                    toolResult.toolName(),
                    toolResult.text(),
                    toolResult.success(),
                    toolResult.elapsedMillis()
            );
            return;
        }
        if (event instanceof AgentEvent.UsageReported usage) {
            window.showUsage(usage.usage(), usage.maxContextTokens());
            return;
        }
        if (event instanceof AgentEvent.Done) {
            window.showDone();
        }
    }
}
