package com.aster.core.context;

import com.aster.llm.model.Message;
import com.aster.llm.model.ToolCall;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 校验 OpenAI 兼容协议里的工具调用消息顺序。
 *
 * <p>它能抓住一种常见压缩错误：assistant tool_call 被保留，
 * 但对应工具结果被摘要吃掉；或者反过来只保留了工具结果。</p>
 */
public final class ToolProtocolValidator {
    private ToolProtocolValidator() {
    }

    /**
     * 当消息列表违反工具调用协议时抛出异常。
     */
    public static void validate(List<Message> messages) {
        // pendingToolCallIds 表示 assistant 已经请求调用、但还没收到结果的工具。
        // OpenAI 兼容协议要求这些 id 必须由紧随其后的 role=tool 消息消费掉。
        Set<String> pendingToolCallIds = new LinkedHashSet<>();

        for (Message message : messages) {
            String role = message.role();

            // assistant 一旦请求工具，后面必须立刻跟对应 tool 结果。
            if (!pendingToolCallIds.isEmpty() && !"tool".equals(role)) {
                throw new IllegalStateException("tool_call missing tool result before role=" + role);
            }

            if ("assistant".equals(role)) {
                // assistant 可以是普通文本，也可以带 tool_calls。
                // 但 assistant 自己不能带 tool_call_id；tool_call_id 只属于 role=tool。
                validateAssistantMessage(message, pendingToolCallIds);
                continue;
            }

            if ("tool".equals(role)) {
                // tool 消息不能孤立出现，必须响应前面某个 assistant.tool_calls.id。
                validateToolMessage(message, pendingToolCallIds);
                continue;
            }

            if ("user".equals(role) || "system".equals(role)) {
                validatePlainMessage(message);
                continue;
            }

            throw new IllegalStateException("Unknown message role: " + role);
        }

        if (!pendingToolCallIds.isEmpty()) {
            throw new IllegalStateException("unanswered tool_call ids: " + pendingToolCallIds);
        }
    }

    private static void validateAssistantMessage(Message message, Set<String> pendingToolCallIds) {
        if (message.toolCallId() != null) {
            throw new IllegalStateException("assistant message must not have tool_call_id");
        }
        if (!message.hasToolCalls() && (message.content() == null || message.content().isBlank())) {
            // OpenAI-compatible Chat API 要求 assistant 至少有 content 或 tool_calls。
            // 如果历史里只有 reasoning_content，Message 构造器会先把它降级成 content。
            throw new IllegalStateException("assistant message must have content or tool_calls");
        }

        for (ToolCall call : message.toolCalls()) {
            if (call.id() == null || call.id().isBlank()) {
                throw new IllegalStateException("assistant tool_call must have id");
            }
            pendingToolCallIds.add(call.id());
        }
    }

    private static void validateToolMessage(Message message, Set<String> pendingToolCallIds) {
        if (message.hasToolCalls()) {
            throw new IllegalStateException("tool message must not have tool_calls");
        }
        if (message.reasoningContent() != null) {
            throw new IllegalStateException("tool message must not have reasoning_content");
        }
        if (message.toolCallId() == null || !pendingToolCallIds.remove(message.toolCallId())) {
            // 常见触发场景：压缩时保留了工具结果，
            // 但对应的 assistant.tool_calls 已经被摘要吃掉。
            throw new IllegalStateException("orphan tool result: " + message.toolCallId());
        }
    }

    private static void validatePlainMessage(Message message) {
        // 摘要消息是普通 user/system 消息，不能泄露工具协议字段。
        // 这个检查专门防“用旧 assistant 消息当摘要模板”导致 tool_calls 残留。
        if (message.hasToolCalls()) {
            throw new IllegalStateException(message.role() + " message must not have tool_calls");
        }
        if (message.toolCallId() != null) {
            throw new IllegalStateException(message.role() + " message must not have tool_call_id");
        }
        if (message.reasoningContent() != null) {
            throw new IllegalStateException(message.role() + " message must not have reasoning_content");
        }
    }
}
