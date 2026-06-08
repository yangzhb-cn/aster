package com.aster;

import com.aster.app.rag.JsonlRagSessionStore;
import com.aster.app.rag.JsonlRagStore;
import com.aster.app.rag.RagChatService;
import com.aster.app.rag.RagPromptBuilder;
import com.aster.app.rag.model.RagAnswer;
import com.aster.app.rag.model.RagChunk;
import com.aster.app.rag.model.RagDocument;
import com.aster.app.rag.model.RagVector;
import com.aster.app.rag.retrieve.VectorRetriever;
import com.aster.core.session.SessionIndex;
import com.aster.llm.common.ModelCapability;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.embedding.model.EmbeddingRequest;
import com.aster.llm.embedding.model.EmbeddingResponse;
import com.aster.llm.text.model.ChatRequest;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.llm.text.model.ProviderStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 流式问答服务测试。
 */
class RagChatServiceTest {
    @TempDir
    Path tempDir;

    /**
     * 验证 RAG 问答逐 token 回调，并且不接受不属于当前 provider 的 chat 模型名。
     */
    @Test
    void streamsTokensAndFallsBackToRagProviderModel() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonlRagStore store = new JsonlRagStore(
                objectMapper,
                tempDir.resolve("knowledge-bases"),
                tempDir.resolve("documents"),
                tempDir.resolve("chunks"),
                tempDir.resolve("indexes")
        );
        String now = Instant.now().toString();
        store.saveIngestedDocument(
                new RagDocument("doc_1", "default", "note.md", "", "", "", 1, now, now, false),
                List.of(new RagChunk("default", "doc_1", "chunk_1", 0, "note.md", 0, 5, "hello knowledge")),
                List.of(new RagVector("default", "chunk_1", "embed", List.of(1.0, 0.0)))
        );

        AtomicReference<ChatRequest> requestRef = new AtomicReference<>();
        StreamingChatClient chatClient = (request, handler) -> {
            requestRef.set(request);
            handler.onEvent(new ProviderStreamEvent.TextDelta("hello"));
            handler.onEvent(new ProviderStreamEvent.TextDelta(" world"));
            handler.onDone();
        };
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                "deepseek",
                "http://localhost/v1",
                "key",
                "deepseek-v4-flash",
                false,
                null,
                true,
                List.of("deepseek-v4-flash", "deepseek-v4-pro"),
                Set.of(ModelCapability.CHAT_COMPLETIONS),
                true
        );
        RagChatService service = new RagChatService(
                new SessionIndex(objectMapper, tempDir.resolve("rag-sessions"), "rag_sess_"),
                new JsonlRagSessionStore(objectMapper, tempDir.resolve("rag-sessions")),
                new VectorRetriever(
                        store,
                        (EmbeddingRequest request) -> new EmbeddingResponse(request.model(), List.of(List.of(1.0, 0.0))),
                        "embed"
                ),
                chatClient,
                provider,
                "你是知识库助手。",
                new RagPromptBuilder()
        );

        List<String> tokens = new ArrayList<>();
        AtomicReference<RagAnswer> answerRef = new AtomicReference<>();
        service.stream("", "default", "问题", 1, "qwen3:latest", new RagChatService.RagStreamHandler() {
            @Override
            public void onStarted(RagChatService.RagStreamStart start) {
                assertEquals("deepseek-v4-flash", start.chatModel());
                assertEquals(1, start.hits().size());
            }

            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onDone(RagAnswer result) {
                answerRef.set(result);
            }
        });

        assertEquals(List.of("hello", " world"), tokens);
        assertEquals("hello world", answerRef.get().answer());
        assertEquals("deepseek-v4-flash", requestRef.get().model());
        assertTrue(requestRef.get().tools().isEmpty());
    }
}
