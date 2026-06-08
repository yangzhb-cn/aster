package com.aster.llm.text.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.llm.text.model.ChatStreamChunk;
import com.aster.llm.text.model.ProviderStreamEvent;
import com.aster.llm.text.model.TokenUsage;
import com.aster.llm.text.model.ToolCallDelta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OpenAI-compatible SSE 数据解析器。
 *
 * <p>这一层专门吃 OpenAI / DeepSeek / Kimi 这类兼容接口的 {@code data: ...}
 * 内容，并输出项目内部统一的 ProviderStreamEvent。以后接 Anthropic 或 Google 时，
 * 新增对应 Parser 即可，AgentLoop 不需要认识各家原始 JSON。</p>
 */
public class OpenAiCompatibleStreamParser {
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleStreamParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * 把一条 SSE data 内容转成零到多条统一供应商事件。
     */
    public List<ProviderStreamEvent> parse(String data) throws IOException {
        if ("[DONE]".equals(data)) {
            return List.of(new ProviderStreamEvent.Done());
        }

        ChatStreamChunk chunk = objectMapper.readValue(data, ChatStreamChunk.class);
        List<ProviderStreamEvent> events = new ArrayList<>();
        appendChoiceEvents(chunk, events);
        appendUsageEvent(chunk, events);
        return events;
    }

    /**
     * 解析 choices/delta 中的正文、reasoning 和工具调用增量。
     */
    private void appendChoiceEvents(ChatStreamChunk chunk, List<ProviderStreamEvent> events) {
        if (chunk.choices() == null) {
            return;
        }

        for (ChatStreamChunk.Choice choice : chunk.choices()) {
            ChatStreamChunk.ChatDelta delta = choice.delta();
            if (delta == null) {
                continue;
            }

            if (delta.content() != null) {
                events.add(new ProviderStreamEvent.TextDelta(delta.content()));
            }
            if (delta.reasoningContent() != null) {
                events.add(new ProviderStreamEvent.ReasoningDelta(delta.reasoningContent()));
            }
            appendToolCallEvents(delta.toolCalls(), events);
        }
    }

    /**
     * 工具调用可能被拆成多段，所以每段单独发一条统一事件。
     */
    private void appendToolCallEvents(List<ToolCallDelta> toolCallDeltas, List<ProviderStreamEvent> events) {
        if (toolCallDeltas == null) {
            return;
        }

        for (ToolCallDelta delta : toolCallDeltas) {
            events.add(new ProviderStreamEvent.ToolCallDeltaPart(delta));
        }
    }

    /**
     * usage 通常出现在流式响应末尾，不属于 assistant 正文。
     */
    private void appendUsageEvent(ChatStreamChunk chunk, List<ProviderStreamEvent> events) {
        if (chunk.usage() == null) {
            return;
        }

        events.add(new ProviderStreamEvent.UsageDelta(TokenUsage.from(chunk.usage())));
    }
}
