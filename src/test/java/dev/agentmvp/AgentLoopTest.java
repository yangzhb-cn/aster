package dev.agentmvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.agent.AgentEventHandler;
import dev.agentmvp.agent.AgentLoop;
import dev.agentmvp.agent.model.AgentEvent;
import dev.agentmvp.context.ContextBuilder;
import dev.agentmvp.context.SimpleTokenEstimator;
import dev.agentmvp.context.TranscriptSummarizer;
import dev.agentmvp.context.model.ContextOptions;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.ChatStreamChunk;
import dev.agentmvp.llm.model.ToolCallDelta;
import dev.agentmvp.mcp.McpToolExecutor;
import dev.agentmvp.session.InMemorySessionStore;
import dev.agentmvp.tool.LocalToolExecutor;
import dev.agentmvp.tool.ParallelToolExecutor;
import dev.agentmvp.tool.ToolRegistry;
import dev.agentmvp.tool.model.Tool;
import dev.agentmvp.tool.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentLoop 的主链路测试。
 *
 * <p>这里用假的流式 LLM 模拟 SSE 片段，验证流式文本、流式 tool_call
 * 和工具结果回灌能串起来，不依赖真实模型 API。</p>
 */
class AgentLoopTest {
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

        Queue<List<ChatStreamChunk>> scriptedStreams = new ArrayDeque<>(List.of(
                List.of(
                        toolCallChunk(0, "call_1", "echo", "{\"text\":"),
                        toolCallChunk(0, null, null, "\"hello\"}")
                ),
                List.of(
                        textChunk("do"),
                        textChunk("ne")
                )
        ));
        List<Integer> requestMessageCounts = new ArrayList<>();
        List<AgentEvent> events = new ArrayList<>();

        StreamingChatClient fakeStreamingLlm = (request, handler) -> {
            requestMessageCounts.add(request.messages().size());
            for (ChatStreamChunk chunk : scriptedStreams.remove()) {
                handler.onChunk(chunk);
            }
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
                events::add,
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
            handler.onChunk(reasoningChunk("先分析问题。"));
            handler.onChunk(textChunk("最终回答"));
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
                events::add,
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
            handler.onChunk(textChunk("plain answer"));
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
                AgentEventHandler.noop(),
                4
        );

        assertEquals("plain answer", loop.run("hello"));
    }

    /**
     * 构造一片文本 SSE 片段。
     */
    private static ChatStreamChunk textChunk(String text) {
        return new ChatStreamChunk(List.of(
                new ChatStreamChunk.Choice(
                        new ChatStreamChunk.ChatDelta("assistant", text, null, null),
                        null
                )
        ));
    }

    /**
     * 构造一片 reasoning_content SSE 片段。
     */
    private static ChatStreamChunk reasoningChunk(String text) {
        return new ChatStreamChunk(List.of(
                new ChatStreamChunk.Choice(
                        new ChatStreamChunk.ChatDelta("assistant", null, text, null),
                        null
                )
        ));
    }

    /**
     * 构造一片 tool_call SSE 片段，用来模拟参数被分段返回的情况。
     */
    private static ChatStreamChunk toolCallChunk(int index, String id, String name, String arguments) {
        return new ChatStreamChunk(List.of(
                new ChatStreamChunk.Choice(
                        new ChatStreamChunk.ChatDelta(
                                "assistant",
                                null,
                                null,
                                List.of(new ToolCallDelta(
                                        index,
                                        id,
                                        id == null ? null : "function",
                                        new ToolCallDelta.FunctionDelta(name, arguments)
                                ))
                        ),
                        null
                )
        ));
    }
}
