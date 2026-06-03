package com.aster.ui.web;

import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web 事件 DTO 转换器。
 *
 * <p>核心事件模型使用 Java sealed record。Web 前端更适合消费
 * {@code type + payload} 结构，所以这里做一次薄转换，不改变 core/event。</p>
 */
public final class WebAgentEventMapper {
    private WebAgentEventMapper() {
    }

    /**
     * 把 AgentEventEnvelope 转成前端可直接消费的 Map。
     */
    public static Map<String, Object> toMap(AgentEventEnvelope envelope) {
        Map<String, Object> result = new LinkedHashMap<>();
        AgentEvent event = envelope.event();
        result.put("type", typeOf(event));
        result.put("meta", Map.of(
                "eventId", envelope.meta().eventId(),
                "runId", envelope.meta().runId(),
                "sessionName", envelope.meta().sessionName(),
                "sequence", envelope.meta().sequence(),
                "timestamp", envelope.meta().timestamp().toString()
        ));
        result.put("payload", payloadOf(event));
        return result;
    }

    private static String typeOf(AgentEvent event) {
        return event.getClass().getSimpleName();
    }

    private static Map<String, Object> payloadOf(AgentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (event instanceof AgentEvent.RunStarted value) {
            payload.put("userInput", value.userInput());
        } else if (event instanceof AgentEvent.RunFinished value) {
            payload.put("finalText", value.finalText());
        } else if (event instanceof AgentEvent.RunFailed value) {
            payload.put("errorMessage", value.errorMessage());
        } else if (event instanceof AgentEvent.RunQueued value) {
            payload.put("userInput", value.userInput());
            payload.put("queueSize", value.queueSize());
        } else if (event instanceof AgentEvent.SteerReceived value) {
            payload.put("text", value.text());
            payload.put("pendingCount", value.pendingCount());
        } else if (event instanceof AgentEvent.RunStopped value) {
            payload.put("finalText", value.finalText());
        } else if (event instanceof AgentEvent.TurnStarted value) {
            payload.put("round", value.round());
        } else if (event instanceof AgentEvent.TurnFinished value) {
            payload.put("round", value.round());
            payload.put("reason", value.reason());
        } else if (event instanceof AgentEvent.ContextBuilt value) {
            payload.put("compressed", value.compressed());
            payload.put("beforeTokens", value.beforeTokens());
            payload.put("afterTokens", value.afterTokens());
            payload.put("maxContextTokens", value.maxContextTokens());
        } else if (event instanceof AgentEvent.LlmRequestStarted value) {
            payload.put("round", value.round());
            payload.put("model", value.model());
            payload.put("messageCount", value.messageCount());
            payload.put("toolCount", value.toolCount());
        } else if (event instanceof AgentEvent.LlmRequestFinished value) {
            payload.put("round", value.round());
        } else if (event instanceof AgentEvent.MessageStarted value) {
            payload.put("round", value.round());
            payload.put("role", value.role());
        } else if (event instanceof AgentEvent.MessageFinished value) {
            payload.put("round", value.round());
            payload.put("role", value.role());
            payload.put("hasToolCalls", value.hasToolCalls());
        } else if (event instanceof AgentEvent.AssistantToken value) {
            payload.put("text", value.text());
        } else if (event instanceof AgentEvent.ReasoningToken value) {
            payload.put("text", value.text());
        } else if (event instanceof AgentEvent.ToolCallStart value) {
            payload.put("toolCallId", value.toolCallId());
            payload.put("toolName", value.toolName());
            payload.put("argumentsJson", value.argumentsJson());
        } else if (event instanceof AgentEvent.ToolCallDone value) {
            payload.put("toolCallId", value.toolCallId());
            payload.put("toolName", value.toolName());
            payload.put("text", value.text());
            payload.put("success", value.success());
            payload.put("elapsedMillis", value.elapsedMillis());
        } else if (event instanceof AgentEvent.UsageReported value) {
            payload.put("usage", value.usage());
            payload.put("maxContextTokens", value.maxContextTokens());
        } else if (event instanceof AgentEvent.Done value) {
            payload.put("finalText", value.finalText());
        }
        return payload;
    }
}
