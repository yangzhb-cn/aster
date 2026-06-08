package com.aster.app.rag;

import com.aster.app.rag.model.RagAnswer;
import com.aster.app.rag.model.RagHit;
import com.aster.app.rag.model.RagSessionRecord;
import com.aster.app.rag.retrieve.VectorRetriever;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.model.SessionRecord;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.llm.text.model.ProviderStreamEvent;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 无工具调用的 RAG 问答服务。
 *
 * <p>这里不走 AgentLoop，不给 LLM 暴露任何工具。后端先固定检索知识库，
 * 再把片段放进 prompt，最后调用 chat 模型回答。</p>
 */
public class RagChatService {
    private final SessionIndex sessionIndex;
    private final JsonlRagSessionStore sessionStore;
    private final VectorRetriever retriever;
    private final StreamingChatClient chatClient;
    private final OpenAiCompatibleProvider chatProvider;
    private final String systemPrompt;
    private final RagPromptBuilder promptBuilder;

    public RagChatService(
            SessionIndex sessionIndex,
            JsonlRagSessionStore sessionStore,
            VectorRetriever retriever,
            StreamingChatClient chatClient,
            OpenAiCompatibleProvider chatProvider,
            String systemPrompt,
            RagPromptBuilder promptBuilder
    ) {
        this.sessionIndex = Objects.requireNonNull(sessionIndex);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.retriever = Objects.requireNonNull(retriever);
        this.chatClient = Objects.requireNonNull(chatClient);
        this.chatProvider = Objects.requireNonNull(chatProvider);
        this.systemPrompt = Objects.requireNonNull(systemPrompt);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
    }

    /**
     * 执行一次 RAG 问答。
     */
    public RagAnswer ask(String sessionId, String kbId, String question, int topK, String chatModel) throws IOException {
        StringBuilder answer = new StringBuilder();
        final RagAnswer[] completed = new RagAnswer[1];
        stream(sessionId, kbId, question, topK, chatModel, new RagStreamHandler() {
            @Override
            public void onStarted(RagStreamStart start) {
            }

            @Override
            public void onToken(String token) {
                answer.append(token);
            }

            @Override
            public void onDone(RagAnswer result) {
                completed[0] = result;
            }
        });
        return completed[0];
    }

    /**
     * 流式执行一次 RAG 问答。
     *
     * <p>先创建/定位 RAG session、写入用户问题和完成检索，
     * 然后把检索命中发给前端，最后逐 token 推送模型回答。</p>
     */
    public void stream(
            String sessionId,
            String kbId,
            String question,
            int topK,
            String chatModel,
            RagStreamHandler handler
    ) throws IOException {
        String cleanedQuestion = requireQuestion(question);
        SessionRecord session = ensureSession(sessionId, cleanedQuestion);
        List<RagSessionRecord> history = sessionStore.load(session.id());
        sessionStore.appendUser(session.id(), cleanedQuestion);

        List<RagHit> hits = retriever.retrieve(kbId, cleanedQuestion, topK);
        String model = selectChatModel(chatModel);
        handler.onStarted(new RagStreamStart(
                session,
                kbId,
                cleanedQuestion,
                model,
                retriever.embeddingModel(),
                hits
        ));
        String answer = generateAnswer(model, promptBuilder.buildUserPrompt(cleanedQuestion, hits, history), handler);
        sessionStore.appendAssistant(session.id(), answer, kbId, model, retriever.embeddingModel(), hits);
        sessionIndex.touch(session.id());
        SessionRecord touched = sessionIndex.get(session.id()).orElse(session);
        handler.onDone(new RagAnswer(touched, kbId, cleanedQuestion, answer, model, retriever.embeddingModel(), hits));
    }

    /**
     * RAG 当前可用的默认 chat 模型。
     */
    public String defaultChatModel() {
        return chatProvider.defaultModel();
    }

    /**
     * RAG 当前可切换的 chat 模型列表。
     */
    public List<String> availableChatModels() {
        return chatProvider.switchableChatModels();
    }

    /**
     * RAG 当前使用的 embedding 模型。
     */
    public String embeddingModel() {
        return retriever.embeddingModel();
    }

    private String generateAnswer(String model, String userPrompt, RagStreamHandler handler) throws IOException {
        StringBuilder output = new StringBuilder();
        ChatRequest request = ChatRequest.streaming(
                model,
                List.of(Message.system(systemPrompt), Message.user(userPrompt)),
                List.of(),
                null,
                false,
                null,
                chatProvider.streamUsageEnabled()
        );
        chatClient.stream(request, event -> {
            if (event instanceof ProviderStreamEvent.TextDelta delta) {
                output.append(delta.text());
                handler.onToken(delta.text());
            }
        });
        String answer = output.toString().strip();
        return answer.isBlank() ? "当前模型没有返回回答。" : answer;
    }

    private String selectChatModel(String chatModel) {
        String normalized = chatModel == null ? "" : chatModel.strip();
        if (!normalized.isBlank() && chatProvider.switchableChatModels().contains(normalized)) {
            return normalized;
        }
        return chatProvider.defaultModel();
    }

    private SessionRecord ensureSession(String sessionId, String question) throws IOException {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionIndex.get(sessionId)
                    .filter(record -> !record.archived())
                    .orElseThrow(() -> new IOException("rag session not found: " + sessionId));
        }
        return sessionIndex.create(firstQuestionDisplayName(question));
    }

    private String requireQuestion(String question) throws IOException {
        String cleaned = question == null ? "" : question.strip();
        if (cleaned.isBlank()) {
            throw new IOException("question is required");
        }
        return cleaned;
    }

    private String firstQuestionDisplayName(String question) {
        String compact = question == null ? "" : question.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "新知识库问答";
        }
        return compact.length() > 18 ? compact.substring(0, 18) + "..." : compact;
    }

    /**
     * RAG 流式回调。
     */
    public interface RagStreamHandler {
        /**
         * 检索完成并准备开始回答。
         */
        void onStarted(RagStreamStart start);

        /**
         * 收到模型文本增量。
         */
        void onToken(String token);

        /**
         * 回答完成并已写入 session。
         */
        void onDone(RagAnswer result);
    }

    /**
     * RAG 流开始事件。
     */
    public record RagStreamStart(
            SessionRecord session,
            String kbId,
            String question,
            String chatModel,
            String embeddingModel,
            List<RagHit> hits
    ) {
        public RagStreamStart {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }
}
