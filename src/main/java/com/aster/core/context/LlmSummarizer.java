package com.aster.core.context;

import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.ChatRequest;
import com.aster.llm.model.Message;
import com.aster.llm.model.ProviderStreamEvent;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通过 LLM 生成旧对话摘要的 Summarizer。
 *
 * <p>它只复用底层 StreamingChatClient，不走 AgentLoop，不注册工具，
 * 也不写入 SessionStore。摘要失败时会回退到确定性的 TranscriptSummarizer，
 * 避免上下文压缩失败影响主对话。</p>
 */
public class LlmSummarizer implements Summarizer {
    private static final String DEFAULT_SUMMARY_PROMPT = "请把历史对话压缩成后续开发可继续接上的中文摘要。";

    private final String model;
    private final StreamingChatClient streamingChatClient;
    private final String summaryPrompt;
    private final int maxSummaryChars;
    private final Summarizer fallback;

    public LlmSummarizer(
            String model,
            StreamingChatClient streamingChatClient,
            String summaryPrompt,
            int maxSummaryChars,
            Summarizer fallback
    ) {
        this.model = Objects.requireNonNull(model);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.summaryPrompt = summaryPrompt == null || summaryPrompt.isBlank()
                ? DEFAULT_SUMMARY_PROMPT
                : summaryPrompt.strip();
        this.maxSummaryChars = maxSummaryChars;
        this.fallback = Objects.requireNonNull(fallback);
    }

    /**
     * 请求 LLM 生成真正的语义摘要。
     *
     * <p>摘要请求不携带 tools，也关闭 thinking。这里只收集 TextDelta；
     * reasoning_content、usage 和 done 事件不进入最终摘要文本。</p>
     */
    @Override
    public String summarize(List<Message> oldMessages) {
        try {
            String transcript = renderTranscript(oldMessages);
            ChatRequest request = ChatRequest.streaming(
                    model,
                    List.of(
                            Message.system(summaryPrompt),
                            Message.user("## 历史对话转写\n\n" + transcript)
                    ),
                    List.of(),
                    null,
                    false,
                    null,
                    false
            );
            String summary = streamSummary(request).strip();
            if (summary.isBlank()) {
                return fallback.summarize(oldMessages);
            }
            return trim(summary);
        } catch (IOException | RuntimeException error) {
            return fallback.summarize(oldMessages);
        }
    }

    private String streamSummary(ChatRequest request) throws IOException {
        StringBuilder text = new StringBuilder();
        streamingChatClient.stream(request, event -> {
            if (event instanceof ProviderStreamEvent.TextDelta delta) {
                text.append(delta.text());
            }
        });
        return text.toString();
    }

    private String renderTranscript(List<Message> messages) {
        return messages.stream()
                .map(this::render)
                .collect(Collectors.joining("\n"));
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

    private String trim(String summary) {
        if (maxSummaryChars <= 0 || summary.length() <= maxSummaryChars) {
            return summary;
        }
        return summary.substring(0, maxSummaryChars) + "\n...[summary truncated after LLM summarization]";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
