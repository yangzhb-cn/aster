package dev.agentmvp.agent;

import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.ProviderStreamEvent;
import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.llm.model.ToolCallDelta;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 根据流式增量重建一条 assistant 消息。
 *
 * <p>SSE 片段不是完整消息。文本会按 token 分段到达，
 * 工具参数也可能被拆成多段。这个构造器会等流结束后，
 * 再产出一条可以写入 SessionStore 的普通 {@link Message}。</p>
 */
public class AssistantMessageBuilder {
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoningContent = new StringBuilder();
    private final Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();

    /**
     * 把一条统一供应商流式事件合并到正在构造的 assistant 消息里。
     */
    public void append(ProviderStreamEvent event) {
        if (event instanceof ProviderStreamEvent.TextDelta delta) {
            content.append(delta.text());
            return;
        }
        if (event instanceof ProviderStreamEvent.ReasoningDelta delta) {
            reasoningContent.append(delta.text());
            return;
        }
        if (event instanceof ProviderStreamEvent.ToolCallDeltaPart delta) {
            appendToolCallDelta(delta.delta());
        }
    }

    /**
     * 收到 {@code data: [DONE]} 后，构造最终 assistant 消息。
     */
    public Message build() {
        if (!toolCalls.isEmpty()) {
            // 工具调用也是按增量到达的。
            // 只有 SSE 流结束后，才能拿到完整函数名和完整 JSON 参数，所以工具要等这里之后再执行。
            var calls = toolCalls.entrySet().stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getKey))
                    .map(entry -> entry.getValue().build())
                    .toList();

            String text = content.isEmpty() ? null : content.toString();
            String reasoningText = reasoningContent.isEmpty() ? null : reasoningContent.toString();
            return Message.assistantToolCalls(text, reasoningText, calls);
        }

        String reasoningText = reasoningContent.isEmpty() ? null : reasoningContent.toString();
        return Message.assistant(content.toString(), reasoningText);
    }

    private void appendToolCallDelta(ToolCallDelta part) {
        toolCalls
                .computeIfAbsent(part.index(), ignored -> new ToolCallBuilder())
                .append(part);
    }

    private static class ToolCallBuilder {
        private String id;
        private String type = "function";
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        void append(ToolCallDelta part) {
            if (part.id() != null && !part.id().isBlank()) {
                id = part.id();
            }
            if (part.type() != null && !part.type().isBlank()) {
                type = part.type();
            }
            if (part.function() == null) {
                return;
            }
            if (part.function().name() != null && !part.function().name().isBlank()) {
                name = part.function().name();
            }
            if (part.function().argumentsJson() != null) {
                arguments.append(part.function().argumentsJson());
            }
        }

        ToolCall build() {
            return new ToolCall(
                    id,
                    type,
                    new ToolCall.FunctionCall(name, arguments.toString())
            );
        }
    }
}
