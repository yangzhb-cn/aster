package dev.agentmvp.app.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.ProviderStreamEvent;
import dev.agentmvp.app.memory.model.MemoryExtractionResult;
import dev.agentmvp.core.session.SessionStore;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 后台长期记忆抽取 Agent。
 *
 * <p>它不是主对话 Agent，不回答用户。它只在每轮对话结束后读取最近对话，
 * 调用 LLM 抽取候选记忆，再交给 MarkdownMemoryStore 做类型校验和写入。</p>
 */
public class MemoryExtractionAgent {
    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 12;

    private final String model;
    private final SessionStore sessionStore;
    private final MarkdownMemoryStore memoryStore;
    private final StreamingChatClient streamingChatClient;
    private final ObjectMapper objectMapper;
    private final String extractionPrompt;
    private final int recentMessageLimit;

    public MemoryExtractionAgent(
            String model,
            SessionStore sessionStore,
            MarkdownMemoryStore memoryStore,
            StreamingChatClient streamingChatClient,
            ObjectMapper objectMapper,
            String extractionPrompt
    ) {
        this(
                model,
                sessionStore,
                memoryStore,
                streamingChatClient,
                objectMapper,
                extractionPrompt,
                DEFAULT_RECENT_MESSAGE_LIMIT
        );
    }

    public MemoryExtractionAgent(
            String model,
            SessionStore sessionStore,
            MarkdownMemoryStore memoryStore,
            StreamingChatClient streamingChatClient,
            ObjectMapper objectMapper,
            String extractionPrompt,
            int recentMessageLimit
    ) {
        this.model = Objects.requireNonNull(model);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.memoryStore = Objects.requireNonNull(memoryStore);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.extractionPrompt = Objects.requireNonNull(extractionPrompt);
        this.recentMessageLimit = recentMessageLimit;
    }

    /**
     * 执行一次长期记忆抽取，并返回写入结果说明。
     */
    public String extract() throws IOException {
        List<Message> recentMessages = recentConversationMessages();
        if (recentMessages.isEmpty()) {
            return "没有可抽取的对话消息";
        }

        String rawJson = callMemoryAgent(recentMessages, memoryStore.load());
        MemoryExtractionResult result = objectMapper.readValue(extractJsonObject(rawJson), MemoryExtractionResult.class);
        int added = memoryStore.appendCandidates(result.memories());
        return "长期记忆抽取完成，新增 " + added + " 条";
    }

    private List<Message> recentConversationMessages() throws IOException {
        List<Message> conversation = sessionStore.loadMessages().stream()
                .filter(message -> !"system".equals(message.role()))
                .toList();
        int fromIndex = Math.max(0, conversation.size() - recentMessageLimit);
        return conversation.subList(fromIndex, conversation.size());
    }

    private String callMemoryAgent(List<Message> recentMessages, String currentMemory) throws IOException {
        StringBuilder output = new StringBuilder();
        ChatRequest request = ChatRequest.streaming(
                model,
                List.of(
                        Message.system(extractionPrompt),
                        Message.user(renderExtractionInput(recentMessages, currentMemory))
                ),
                List.of(),
                null
        );

        streamingChatClient.stream(request, event -> {
            if (event instanceof ProviderStreamEvent.TextDelta delta) {
                output.append(delta.text());
            }
        });
        return output.toString();
    }

    private String renderExtractionInput(List<Message> recentMessages, String currentMemory) {
        return """
                ## 当前长期记忆

                %s

                ## 最近对话

                %s
                """.formatted(currentMemory, renderTranscript(recentMessages));
    }

    private String renderTranscript(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (Message message : messages) {
            builder.append(message.role()).append(": ");
            if (message.content() != null) {
                builder.append(message.content());
            }
            if (message.hasToolCalls()) {
                builder.append(" [tool_calls=").append(message.toolCalls().size()).append("]");
            }
            if (message.toolCallId() != null) {
                builder.append(" [tool_call_id=").append(message.toolCallId()).append("]");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String extractJsonObject(String rawText) {
        int start = rawText.indexOf('{');
        int end = rawText.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Memory Agent 没有返回 JSON 对象: " + rawText);
        }
        return rawText.substring(start, end + 1);
    }
}
