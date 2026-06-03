package dev.agentmvp.ui.tui;

import dev.agentmvp.core.event.AgentEventHandler;
import dev.agentmvp.core.event.model.AgentEvent;
import dev.agentmvp.core.event.model.AgentEventEnvelope;

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
        if (event instanceof AgentEvent.AssistantToken token) {
            window.appendAssistantToken(token.text());
            return;
        }
        if (event instanceof AgentEvent.ReasoningToken token) {
            window.appendReasoningToken(token.text());
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
