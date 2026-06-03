package dev.agentmvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import dev.agentmvp.core.event.AgentEventBus;
import dev.agentmvp.core.agent.AgentLoop;
import dev.agentmvp.core.event.model.AgentEvent;
import dev.agentmvp.core.event.model.AgentEventEnvelope;
import dev.agentmvp.core.context.ContextBuilder;
import dev.agentmvp.core.context.SimpleTokenEstimator;
import dev.agentmvp.core.context.TranscriptSummarizer;
import dev.agentmvp.core.context.model.ContextOptions;
import dev.agentmvp.core.hook.AgentHookPoints;
import dev.agentmvp.core.hook.BeforeToolCallContext;
import dev.agentmvp.core.hook.HookHandler;
import dev.agentmvp.core.hook.HookRegistry;
import dev.agentmvp.core.hook.ToolHookDecision;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.llm.model.ProviderStreamEvent;
import dev.agentmvp.llm.model.TokenUsage;
import dev.agentmvp.llm.model.ToolCallDelta;
import dev.agentmvp.app.memory.LongTermMemoryInjectHook;
import dev.agentmvp.app.memory.MarkdownMemoryStore;
import dev.agentmvp.app.memory.MemoryPromptRenderer;
import dev.agentmvp.app.memory.model.MemoryCandidate;
import dev.agentmvp.app.memory.model.MemoryType;
import dev.agentmvp.app.mcp.McpToolExecutor;
import dev.agentmvp.core.session.InMemorySessionStore;
import dev.agentmvp.core.tool.LocalToolExecutor;
import dev.agentmvp.core.tool.ParallelToolExecutor;
import dev.agentmvp.core.tool.ToolRegistry;
import dev.agentmvp.core.tool.model.Tool;
import dev.agentmvp.core.tool.model.ToolResult;
import dev.agentmvp.app.tool.result.ToolResultOffloadHook;
import dev.agentmvp.app.tool.result.ToolResultOffloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentLoop 的主链路测试。
 *
 * <p>这里用假的流式 LLM 模拟 ProviderStreamEvent，验证流式文本、流式 tool_call
 * 和工具结果回灌能串起来，不依赖真实模型 API。</p>
 */
class AgentLoopTest {
    @TempDir
    Path tempDir;

    /**
     * 验证模型先流式返回 tool_call，工具执行后再继续请求模型拿最终答案。
     */
    @Test
    void streamsToolCallsAndWritesToolResultsBeforeFinalAnswer() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();

        LocalToolExecutor localExecutor = new LocalToolExecutor(objectMapper);
        ToolRegistry toolRegistry = new ToolRegistry(localExecutor, new McpToolExecutor());
        toolRegistry.registerLocal(
                Tool.local(
                        "echo",
                        "Echo",
                        "Echo text back to the model.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of("text", Map.of("type", "string")),
                                "required", List.of("text")
                        )
                ),
                (call, arguments) -> ToolResult.text(call.id(), "echo:" + arguments.get("text"))
        );

        Queue<List<ProviderStreamEvent>> scriptedStreams = new ArrayDeque<>(List.of(
                List.of(
                        toolCallEvent(0, "call_1", "echo", "{\"text\":"),
                        toolCallEvent(0, null, null, "\"hello\"}")
                ),
                List.of(
                        textEvent("do"),
                        textEvent("ne")
                )
        ));
        List<Integer> requestMessageCounts = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            requestMessageCounts.add(request.messages().size());
            for (ProviderStreamEvent event : scriptedStreams.remove()) {
                handler.onEvent(event);
            }
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                eventBus(events),
                4
        );

        String answer = loop.run("say hello");

        assertEquals("done", answer);
        assertEquals(4, sessionStore.loadMessages().size());
        assertTrue(sessionStore.loadMessages().stream().anyMatch(message ->
                "tool".equals(message.role()) && "call_1".equals(message.toolCallId())
        ));

        // 第二次 LLM 调用应该能看到 user + assistant tool_call + 工具结果。
        assertEquals(List.of(1, 3), requestMessageCounts);
        assertEquals(List.of("do", "ne"), events.stream()
                .filter(event -> event instanceof AgentEvent.AssistantToken)
                .map(event -> ((AgentEvent.AssistantToken) event).text())
                .toList());
    }

    /**
     * 验证 beforeToolCall Hook 可以拒绝工具执行，同时仍写回配对的 tool 错误消息。
     */
    @Test
    void beforeToolCallHookCanDenyToolExecutionWithoutBreakingProtocol() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        AtomicInteger executed = new AtomicInteger();

        LocalToolExecutor localExecutor = new LocalToolExecutor(objectMapper);
        ToolRegistry toolRegistry = new ToolRegistry(localExecutor, new McpToolExecutor());
        toolRegistry.registerLocal(
                Tool.local(
                        "dangerous_bash",
                        "Dangerous bash",
                        "Dangerous command.",
                        Map.of("type", "object", "properties", Map.of())
                ),
                (call, arguments) -> {
                    executed.incrementAndGet();
                    return ToolResult.text(call.id(), "should not run");
                }
        );

        Queue<List<ProviderStreamEvent>> scriptedStreams = new ArrayDeque<>(List.of(
                List.of(toolCallEvent(0, "call_danger", "dangerous_bash", "{}")),
                List.of(textEvent("done"))
        ));
        HookHandler<BeforeToolCallContext, ToolHookDecision> denyDangerousTool =
                context -> ToolHookDecision.deny("高危险工具需要审批");
        HookRegistry hookRegistry = HookRegistry.empty();
        hookRegistry.register(AgentHookPoints.BEFORE_TOOL_CALL, denyDangerousTool);

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            for (ProviderStreamEvent event : scriptedStreams.remove()) {
                handler.onEvent(event);
            }
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                hookRegistry,
                AgentEventBus.noop("test"),
                4
        );

        assertEquals("done", loop.run("run dangerous tool"));
        assertEquals(0, executed.get());

        Message toolMessage = sessionStore.loadMessages().stream()
                .filter(message -> "tool".equals(message.role()))
                .findFirst()
                .orElseThrow();
        assertEquals("call_danger", toolMessage.toolCallId());
        assertTrue(toolMessage.content().contains("工具调用被 Hook 拒绝执行"));
        assertTrue(toolMessage.content().contains("高危险工具需要审批"));
    }

    /**
     * 验证大工具结果会写入 JSONL，session 里只保留路径和预览。
     */
    @Test
    void offloadsLargeToolResultsIntoJsonlBeforeWritingToolMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        String largeText = "large-tool-output-" + "x".repeat(200);

        LocalToolExecutor localExecutor = new LocalToolExecutor(objectMapper);
        ToolRegistry toolRegistry = new ToolRegistry(localExecutor, new McpToolExecutor());
        toolRegistry.registerLocal(
                Tool.local(
                        "large_echo",
                        "Large echo",
                        "Return a large result.",
                        Map.of("type", "object", "properties", Map.of())
                ),
                (call, arguments) -> ToolResult.text(call.id(), largeText)
        );

        Queue<List<ProviderStreamEvent>> scriptedStreams = new ArrayDeque<>(List.of(
                List.of(toolCallEvent(0, "call_large", "large_echo", "{}")),
                List.of(textEvent("done"))
        ));

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            for (ProviderStreamEvent event : scriptedStreams.remove()) {
                handler.onEvent(event);
            }
            handler.onDone();
        };

        HookRegistry hookRegistry = HookRegistry.empty();
        hookRegistry.register(
                AgentHookPoints.BEFORE_TOOL_RESULT_APPEND,
                new ToolResultOffloadHook(new ToolResultOffloader(objectMapper, tempDir, 30, 20))
        );

        AgentLoop loop = new AgentLoop(
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                hookRegistry,
                AgentEventBus.noop("test"),
                4
        );

        assertEquals("done", loop.run("run large tool"));

        String toolContent = sessionStore.loadMessages().stream()
                .filter(message -> "tool".equals(message.role()))
                .findFirst()
                .orElseThrow()
                .content();

        assertTrue(toolContent.contains("完整结果已卸载到 JSONL 文件"));
        assertTrue(toolContent.contains("jsonlPath="));
        assertTrue(toolContent.contains("inlinePreview:"));

        Path storedPath;
        try (Stream<Path> files = Files.list(tempDir)) {
            storedPath = files
                    .filter(path -> path.toString().endsWith(".jsonl"))
                    .findFirst()
                    .orElseThrow();
        }
        JsonNode stored = objectMapper.readTree(Files.readString(storedPath));

        assertEquals("call_large", stored.path("toolCallId").asText());
        assertEquals("large_echo", stored.path("toolName").asText());
        assertEquals(largeText, stored.path("text").asText());
    }

    /**
     * 验证 AgentLoop 会发出完整的事件流骨架，方便后续 Web SSE、Hook、扩展系统复用。
     */
    @Test
    void emitsLifecycleContextAndMessageBoundaryEvents() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            handler.onEvent(textEvent("answer"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                eventBus(events),
                4
        );

        assertEquals("answer", loop.run("hello"));

        assertEquals(List.of(
                "RunStarted",
                "MessageStarted",
                "MessageFinished",
                "TurnStarted",
                "ContextBuilt",
                "LlmRequestStarted",
                "MessageStarted",
                "AssistantToken",
                "LlmRequestFinished",
                "MessageFinished",
                "TurnFinished",
                "Done",
                "RunFinished"
        ), events.stream().map(event -> event.getClass().getSimpleName()).toList());

        AgentEvent.ContextBuilt contextBuilt = (AgentEvent.ContextBuilt) events.get(4);
        assertEquals(false, contextBuilt.compressed());
        assertEquals(128_000, contextBuilt.maxContextTokens());

        AgentEvent.LlmRequestStarted requestStarted = (AgentEvent.LlmRequestStarted) events.get(5);
        assertEquals(1, requestStarted.round());
        assertEquals("fake-model", requestStarted.model());
        assertEquals(1, requestStarted.messageCount());
        assertEquals(0, requestStarted.toolCount());

        AgentEvent.TurnFinished turnFinished = (AgentEvent.TurnFinished) events.get(10);
        assertEquals(1, turnFinished.round());
        assertEquals("final", turnFinished.reason());
    }

    /**
     * 验证 EventBus 会给事件补充统一 metadata。
     */
    @Test
    void wrapsEventsWithMetadataEnvelope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );
        List<AgentEventEnvelope> envelopes = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            handler.onEvent(textEvent("answer"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                AgentEventBus.single("metadata-session", envelopes::add),
                4
        );

        assertEquals("answer", loop.run("hello"));

        assertTrue(envelopes.size() > 5);
        String runId = envelopes.get(0).meta().runId();
        for (int i = 0; i < envelopes.size(); i++) {
            AgentEventEnvelope envelope = envelopes.get(i);
            assertEquals("metadata-session", envelope.meta().sessionName());
            assertEquals(runId, envelope.meta().runId());
            assertEquals(i + 1L, envelope.meta().sequence());
            assertTrue(envelope.meta().eventId() != null && !envelope.meta().eventId().isBlank());
            assertTrue(envelope.meta().timestamp() != null);
        }
    }

    /**
     * 验证同一条事件信封可以同时分发给多个出口。
     */
    @Test
    void dispatchesSameEnvelopeToMultipleHandlers() {
        List<AgentEventEnvelope> tuiEvents = new ArrayList<>();
        List<AgentEventEnvelope> webEvents = new ArrayList<>();
        AgentEventBus eventBus = new AgentEventBus("multi-session", List.of(tuiEvents::add, webEvents::add));

        eventBus.beginRun();
        eventBus.publish(new AgentEvent.RunStarted("hello"));

        assertEquals(1, tuiEvents.size());
        assertEquals(1, webEvents.size());
        assertTrue(tuiEvents.get(0) == webEvents.get(0));
        assertEquals("multi-session", tuiEvents.get(0).meta().sessionName());
        assertEquals(1L, tuiEvents.get(0).meta().sequence());
    }

    /**
     * 验证 DeepSeek reasoning_content 会进入事件流，并保存到 assistant message。
     */
    @Test
    void streamsReasoningContentSeparatelyFromAssistantText() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            handler.onEvent(reasoningEvent("先分析问题。"));
            handler.onEvent(textEvent("最终回答"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                eventBus(events),
                4
        );

        assertEquals("最终回答", loop.run("hello"));
        assertEquals(List.of("先分析问题。"), events.stream()
                .filter(event -> event instanceof AgentEvent.ReasoningToken)
                .map(event -> ((AgentEvent.ReasoningToken) event).text())
                .toList());
        assertEquals("先分析问题。", sessionStore.loadMessages().get(1).reasoningContent());
    }

    /**
     * 验证模型只返回 reasoning_content 时，也不会写出非法 assistant 历史。
     */
    @Test
    void convertsReasoningOnlyAssistantIntoSendableContent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            handler.onEvent(reasoningEvent("只有思考，没有可见正文。"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                AgentEventBus.noop("test"),
                4
        );

        assertEquals("只有思考，没有可见正文。", loop.run("hello"));
        assertEquals("只有思考，没有可见正文。", sessionStore.loadMessages().get(1).content());
        assertEquals(null, sessionStore.loadMessages().get(1).reasoningContent());
    }

    /**
     * 验证没有注册工具时，请求体不会带 tool_choice。
     */
    @Test
    void omitsToolChoiceWhenNoToolsAreRegistered() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            assertTrue(request.tools() == null || request.tools().isEmpty());
            assertEquals(null, request.toolChoice());
            assertEquals(Boolean.TRUE, request.stream());
            handler.onEvent(textEvent("plain answer"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                AgentEventBus.noop("test"),
                4
        );

        assertEquals("plain answer", loop.run("hello"));
    }

    /**
     * 验证 before_llm_request Hook 可以改写暴露给模型的工具列表。
     */
    @Test
    void beforeLlmRequestHookCanRewriteVisibleTools() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        LocalToolExecutor localExecutor = new LocalToolExecutor(objectMapper);
        ToolRegistry toolRegistry = new ToolRegistry(localExecutor, new McpToolExecutor());
        toolRegistry.registerLocal(
                Tool.local(
                        "hidden_tool",
                        "Hidden tool",
                        "This tool should be hidden by hook.",
                        Map.of("type", "object", "properties", Map.of())
                ),
                (call, arguments) -> ToolResult.text(call.id(), "hidden")
        );
        HookRegistry hookRegistry = HookRegistry.empty();
        hookRegistry.register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                context -> context.withTools(List.of())
        );
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            assertTrue(request.tools() == null || request.tools().isEmpty());
            assertEquals(null, request.toolChoice());
            handler.onEvent(textEvent("answer"));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                hookRegistry,
                eventBus(events),
                4
        );

        assertEquals("answer", loop.run("hello"));
        AgentEvent.LlmRequestStarted requestStarted = events.stream()
                .filter(event -> event instanceof AgentEvent.LlmRequestStarted)
                .map(event -> (AgentEvent.LlmRequestStarted) event)
                .findFirst()
                .orElseThrow();
        assertEquals(0, requestStarted.toolCount());
    }

    /**
     * 验证 usage chunk 会转成 UsageReported 事件，并带上最大上下文窗口。
     */
    @Test
    void emitsUsageReportedEventFromStreamUsageChunk() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            handler.onEvent(textEvent("answer"));
            handler.onEvent(usageEvent(120, 30, 70, 20, 150));
            handler.onDone();
        };

        AgentLoop loop = new AgentLoop(
                "fake-model",
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                eventBus(events),
                4
        );

        assertEquals("answer", loop.run("hello"));

        AgentEvent.UsageReported usageEvent = events.stream()
                .filter(event -> event instanceof AgentEvent.UsageReported)
                .map(event -> (AgentEvent.UsageReported) event)
                .findFirst()
                .orElseThrow();

        // 有 cache/miss 字段时，input 应该按 cache + miss 计算。
        assertEquals(100, usageEvent.usage().inputTokens());
        assertEquals(30, usageEvent.usage().inputCacheTokens());
        assertEquals(70, usageEvent.usage().inputCacheMissTokens());
        assertEquals(20, usageEvent.usage().outputTokens());
        assertEquals(120, usageEvent.usage().totalTokens());
        assertEquals(128_000, usageEvent.maxContextTokens());
    }

    /**
     * 验证每轮请求前会读取 Markdown 长期记忆，并作为 system message 注入模型请求。
     */
    @Test
    void injectsMarkdownLongTermMemoryBeforeEachLlmRequest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        ToolRegistry toolRegistry = new ToolRegistry(
                new LocalToolExecutor(objectMapper),
                new McpToolExecutor()
        );
        MarkdownMemoryStore memoryStore = new MarkdownMemoryStore(tempDir.resolve("memory.md"));
        memoryStore.appendCandidates(List.of(new MemoryCandidate(
                MemoryType.BEHAVIOR_PREFERENCE,
                "用户要求代码注释使用中文。",
                "用户明确说：中文注释，以后也是"
        )));
        MemoryPromptRenderer memoryPromptRenderer = new MemoryPromptRenderer("长期记忆注入：\n{{memory}}");
        List<List<Message>> capturedRequests = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            capturedRequests.add(request.messages());
            handler.onEvent(textEvent("answer"));
            handler.onDone();
        };
        HookRegistry hookRegistry = HookRegistry.empty();
        hookRegistry.register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                new LongTermMemoryInjectHook(memoryStore, memoryPromptRenderer)
        );

        AgentLoop loop = new AgentLoop(
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(1_000),
                        ContextOptions.defaults()
                ),
                fakeStreamingLlm,
                toolRegistry,
                new ParallelToolExecutor(toolRegistry, Executors.newFixedThreadPool(2)),
                hookRegistry,
                AgentEventBus.noop("test"),
                4
        );

        assertEquals("answer", loop.run("你好"));

        List<Message> requestMessages = capturedRequests.getFirst();
        assertEquals("system", requestMessages.getFirst().role());
        assertTrue(requestMessages.getFirst().content().contains("长期记忆注入"));
        assertTrue(requestMessages.getFirst().content().contains("用户要求代码注释使用中文"));
        assertEquals("user", requestMessages.get(1).role());
    }

    /**
     * 构造一条正文增量事件。
     */
    private static ProviderStreamEvent textEvent(String text) {
        return new ProviderStreamEvent.TextDelta(text);
    }

    /**
     * 构造一条 reasoning_content 增量事件。
     */
    private static ProviderStreamEvent reasoningEvent(String text) {
        return new ProviderStreamEvent.ReasoningDelta(text);
    }

    /**
     * 构造一条工具调用增量事件，用来模拟参数被分段返回的情况。
     */
    private static ProviderStreamEvent toolCallEvent(int index, String id, String name, String arguments) {
        return new ProviderStreamEvent.ToolCallDeltaPart(new ToolCallDelta(
                index,
                id,
                id == null ? null : "function",
                new ToolCallDelta.FunctionDelta(name, arguments)
        ));
    }

    /**
     * 构造一条 usage 事件。
     */
    private static ProviderStreamEvent usageEvent(
            int promptTokens,
            int promptCacheHitTokens,
            int promptCacheMissTokens,
            int completionTokens,
            int totalTokens
    ) {
        int inputTokens = promptCacheHitTokens + promptCacheMissTokens;
        if (inputTokens == 0) {
            inputTokens = promptTokens;
        }
        int finalTotalTokens = totalTokens;
        if (promptCacheHitTokens + promptCacheMissTokens > 0 || totalTokens == 0) {
            finalTotalTokens = inputTokens + completionTokens;
        }
        return new ProviderStreamEvent.UsageDelta(new TokenUsage(
                inputTokens,
                promptCacheHitTokens,
                promptCacheMissTokens,
                completionTokens,
                finalTotalTokens
        ));
    }

    private static AgentEventBus eventBus(List<AgentEvent> events) {
        return AgentEventBus.single("test", envelope -> events.add(envelope.event()));
    }
}
