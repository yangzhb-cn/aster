package com.aster.core.context;

import com.aster.llm.model.Message;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 简单摘要器：把旧消息渲染成一段对话转写文本。
 *
 * <p>这不是语义级 LLM 摘要，而是一个确定性的 MVP 实现。
 * 好处是不用真实 API，也能测试上下文压缩流程。
 * summaryPrompt 已经从 Markdown 注入，后续替换成真正 LLM 摘要器时可以复用。</p>
 */
public class TranscriptSummarizer implements Summarizer {
    private final String summaryPrompt;
    private final int maxChars;

    public TranscriptSummarizer(int maxChars) {
        this("", maxChars);
    }

    public TranscriptSummarizer(String summaryPrompt, int maxChars) {
        this.summaryPrompt = Objects.requireNonNull(summaryPrompt);
        this.maxChars = maxChars;
    }

    /**
     * 把消息列表渲染成长度受限的摘要输入。
     *
     * <p>教学版没有真的调用 LLM，所以这里返回的是“摘要提示词 + 历史转写”的确定性文本。
     * 真正的生产版本可以把这段文本作为 LLM 请求，再返回模型生成的摘要。</p>
     */
    @Override
    public String summarize(List<Message> oldMessages) {
        String transcript = oldMessages.stream()
                .map(this::render)
                .collect(Collectors.joining("\n"));

        String summaryInput = buildSummaryInput(transcript);
        if (summaryInput.length() <= maxChars) {
            return summaryInput;
        }
        return summaryInput.substring(0, maxChars) + "\n...[summary truncated by MVP summarizer]";
    }

    private String buildSummaryInput(String transcript) {
        if (summaryPrompt.isBlank()) {
            return transcript;
        }
        return summaryPrompt.stripTrailing() + "\n\n## 历史对话转写\n\n" + transcript;
    }

    private String render(Message message) {
        if (message.hasToolCalls()) {
            String names = message.toolCalls().stream()
                    .map(call -> call.function().name() + "(" + call.id() + ")")
                    .collect(Collectors.joining(", "));
            return "assistant tool_calls: " + names;
        }
        if ("tool".equals(message.role())) {
            return "tool(" + message.toolCallId() + "): " + nullToEmpty(message.content());
        }
        return message.role() + ": " + nullToEmpty(message.content());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
