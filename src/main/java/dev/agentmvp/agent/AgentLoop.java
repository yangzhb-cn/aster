package dev.agentmvp.agent;

import dev.agentmvp.agent.model.AgentEvent;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.context.ContextBuilder;
import dev.agentmvp.context.model.ContextBuildResult;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.ChatStreamChunk;
import dev.agentmvp.session.SessionStore;
import dev.agentmvp.tool.ParallelToolExecutor;
import dev.agentmvp.tool.ToolRegistry;
import dev.agentmvp.tool.model.ToolResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 的流式主循环。
 *
 * <p>这个项目只保留一条 LLM 调用路径：SSE 流式调用。
 * 主循环一边接收 assistant 增量，一边拼回完整 assistant 消息；
 * 如果模型请求工具，就并行执行工具，再把结果写回会话，继续请求下一轮模型。</p>
 */
public class AgentLoop {
    private final String model;
    private final SessionStore sessionStore;
    private final ContextBuilder contextBuilder;
    private final StreamingChatClient streamingChatClient;
    private final ToolRegistry toolRegistry;
    private final ParallelToolExecutor parallelToolExecutor;
    private final AgentEventHandler eventHandler;
    private final int maxToolRounds;
    private final boolean thinkingEnabled;
    private final String reasoningEffort;

    public AgentLoop(
            String model,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            AgentEventHandler eventHandler,
            int maxToolRounds
    ) {
        this(
                model,
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                eventHandler,
                maxToolRounds,
                false,
                null
        );
    }

    public AgentLoop(
            OpenAiCompatibleProvider provider,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            AgentEventHandler eventHandler,
            int maxToolRounds
    ) {
        this(
                provider.defaultModel(),
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                eventHandler,
                maxToolRounds,
                provider.thinkingEnabled(),
                provider.reasoningEffort()
        );
    }

    private AgentLoop(
            String model,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            AgentEventHandler eventHandler,
            int maxToolRounds,
            boolean thinkingEnabled,
            String reasoningEffort
    ) {
        this.model = Objects.requireNonNull(model);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.contextBuilder = Objects.requireNonNull(contextBuilder);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.parallelToolExecutor = Objects.requireNonNull(parallelToolExecutor);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.maxToolRounds = maxToolRounds;
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    /**
     * 执行一次用户请求，直到模型返回最终答案。
     */
    public String run(String userInput) throws IOException {
        sessionStore.recordRunStarted(userInput);
        try {
            // SessionStore 保存的是完整原始会话。这里不要先压缩再保存，
            // 否则 debug、恢复会话、后续重新压缩都会丢失真实历史。
            sessionStore.append(Message.user(userInput));
            String answer = continueUntilFinal();
            sessionStore.recordRunFinished(answer);
            return answer;
        } catch (IOException | RuntimeException e) {
            try {
                sessionStore.recordRunInterrupted(e.getMessage());
            } catch (IOException ignored) {
                // 记录中断失败不能覆盖真正导致 run 失败的异常。
            }
            throw e;
        }
    }

    /**
     * 持续进行“模型 -> 工具 -> 模型”循环，直到没有新的工具调用。
     */
    private String continueUntilFinal() throws IOException {
        for (int round = 0; round < maxToolRounds; round++) {
            // 每次请求 LLM 之前才构造上下文。
            // 这样 ContextBuilder 可以根据当前 token 压力决定是否压缩，
            // 而 SessionStore 仍然保留未压缩的完整对话转写。
            ContextBuildResult context = contextBuilder.build(sessionStore.loadMessages());
            List<Map<String, Object>> tools = toolRegistry.toLlmToolSchemas();

            // ChatRequest 是真正发给模型的输入。
            // 注意：没有工具时不要传 tool_choice=auto，一些 OpenAI-compatible 服务
            // 会要求 tool_choice 只有在工具列表非空时才合法。
            ChatRequest request = ChatRequest.streaming(
                    model,
                    context.messages(),
                    tools,
                    tools.isEmpty() ? null : "auto",
                    thinkingEnabled,
                    reasoningEffort
            );

            Message assistant = streamAssistantMessage(request);
            // assistant 消息必须先原样写入历史。
            // 如果它包含 tool_calls，后面的 tool 消息要通过 tool_call_id 与它配对。
            sessionStore.append(assistant);

            if (!assistant.hasToolCalls()) {
                // 没有 tool_calls 说明模型已经给出最终自然语言答案，本轮结束。
                eventHandler.onEvent(new AgentEvent.Done(assistant.content()));
                return assistant.content();
            }

            // 有 tool_calls 时，Agent 负责执行工具，并把每个结果写回 role=tool。
            // 写回后继续下一轮 LLM，让模型基于工具结果生成最终回答。
            executeToolCalls(assistant.toolCalls());
        }

        throw new IllegalStateException("Agent stopped after max tool rounds: " + maxToolRounds);
    }

    /**
     * 流式读取一轮 assistant 输出，并拼成完整 assistant 消息。
     */
    private Message streamAssistantMessage(ChatRequest request) throws IOException {
        AssistantMessageBuilder assistantBuilder = new AssistantMessageBuilder();

        streamingChatClient.stream(request, chunk -> {
            assistantBuilder.append(chunk);
            emitTokenEvents(chunk);
        });

        return assistantBuilder.build();
    }

    /**
     * 并行执行所有工具调用，并为每个调用写入一条 tool 结果消息。
     */
    private void executeToolCalls(List<ToolCall> toolCalls) throws IOException {
        for (ToolCall call : toolCalls) {
            eventHandler.onEvent(new AgentEvent.ToolCallStart(
                    call.id(),
                    call.function().name(),
                    call.function().argumentsJson()
            ));
        }

        List<ToolResult> results = parallelToolExecutor.executeAll(toolCalls);

        for (int i = 0; i < results.size(); i++) {
            ToolCall call = toolCalls.get(i);
            ToolResult result = results.get(i);

            // 工具结果必须以 role=tool 写入，并保留原始 tool_call_id。
            sessionStore.append(Message.tool(result.toolCallId(), result.renderText()));
            eventHandler.onEvent(new AgentEvent.ToolCallDone(
                    result.toolCallId(),
                    call.function().name(),
                    result.renderText(),
                    !result.error(),
                    result.elapsedMillis()
            ));
        }
    }

    /**
     * 在完整消息仍在拼接时，先把可见文本增量发给外层。
     */
    private void emitTokenEvents(ChatStreamChunk chunk) {
        if (chunk.choices() == null) {
            return;
        }

        for (ChatStreamChunk.Choice choice : chunk.choices()) {
            ChatStreamChunk.ChatDelta delta = choice.delta();
            if (delta != null && delta.content() != null) {
                eventHandler.onEvent(new AgentEvent.AssistantToken(delta.content()));
            }
            if (delta != null && delta.reasoningContent() != null) {
                eventHandler.onEvent(new AgentEvent.ReasoningToken(delta.reasoningContent()));
            }
        }
    }
}
