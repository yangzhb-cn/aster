package dev.agentmvp.agent;

import dev.agentmvp.agent.model.AgentEvent;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.llm.model.ToolCall;
import dev.agentmvp.context.ContextBuilder;
import dev.agentmvp.context.model.ContextBuildResult;
import dev.agentmvp.hook.AgentHookPoints;
import dev.agentmvp.hook.AfterRunContext;
import dev.agentmvp.hook.BeforeLlmRequestContext;
import dev.agentmvp.hook.BeforeToolCallContext;
import dev.agentmvp.hook.BeforeToolResultAppendContext;
import dev.agentmvp.hook.HookRegistry;
import dev.agentmvp.hook.ToolHookDecision;
import dev.agentmvp.hook.ToolHookDecisionType;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.ChatRequest;
import dev.agentmvp.llm.model.ProviderStreamEvent;
import dev.agentmvp.session.SessionStore;
import dev.agentmvp.tool.ParallelToolExecutor;
import dev.agentmvp.tool.ToolRegistry;
import dev.agentmvp.tool.model.ToolResult;
import dev.agentmvp.tool.result.ToolResultOffloadHook;
import dev.agentmvp.tool.result.ToolResultOffloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final HookRegistry hookRegistry;
    private final AgentEventBus eventBus;
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
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                model,
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                HookRegistry.empty(),
                eventBus,
                maxToolRounds,
                false,
                null
        );
    }

    public AgentLoop(
            String model,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            ToolResultOffloader toolResultOffloader,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                model,
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                registryWithToolResultOffloader(toolResultOffloader),
                eventBus,
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
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                provider.defaultModel(),
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                HookRegistry.empty(),
                eventBus,
                maxToolRounds,
                provider.thinkingEnabled(),
                provider.reasoningEffort()
        );
    }

    public AgentLoop(
            OpenAiCompatibleProvider provider,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            ToolResultOffloader toolResultOffloader,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                provider.defaultModel(),
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                registryWithToolResultOffloader(toolResultOffloader),
                eventBus,
                maxToolRounds,
                provider.thinkingEnabled(),
                provider.reasoningEffort()
        );
    }

    public AgentLoop(
            OpenAiCompatibleProvider provider,
            SessionStore sessionStore,
            ContextBuilder contextBuilder,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                provider.defaultModel(),
                sessionStore,
                contextBuilder,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                hookRegistry,
                eventBus,
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
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
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
        this.hookRegistry = Objects.requireNonNull(hookRegistry);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.maxToolRounds = maxToolRounds;
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    /**
     * 兼容旧构造器：传入 ToolResultOffloader 时，把它注册成工具结果写入前 Hook。
     */
    private static HookRegistry registryWithToolResultOffloader(ToolResultOffloader toolResultOffloader) {
        HookRegistry registry = HookRegistry.empty();
        registry.register(
                AgentHookPoints.BEFORE_TOOL_RESULT_APPEND,
                new ToolResultOffloadHook(toolResultOffloader)
        );
        return registry;
    }

    /**
     * 执行一次用户请求，直到模型返回最终答案。
     */
    public String run(String userInput) throws IOException {
        eventBus.beginRun();
        publish(new AgentEvent.RunStarted(userInput));
        sessionStore.recordRunStarted(userInput);
        try {
            // SessionStore 保存的是完整原始会话。这里不要先压缩再保存，
            // 否则 debug、恢复会话、后续重新压缩都会丢失真实历史。
            publish(new AgentEvent.MessageStarted(0, "user"));
            sessionStore.append(Message.user(userInput));
            publish(new AgentEvent.MessageFinished(0, "user", false));
            String answer = continueUntilFinal();
            sessionStore.recordRunFinished(answer);
            publish(new AgentEvent.RunFinished(answer));
            hookRegistry.fireQuietly(AgentHookPoints.AFTER_RUN, new AfterRunContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    userInput,
                    answer
            ));
            return answer;
        } catch (IOException | RuntimeException e) {
            try {
                sessionStore.recordRunInterrupted(e.getMessage());
            } catch (IOException ignored) {
                // 记录中断失败不能覆盖真正导致 run 失败的异常。
            }
            publish(new AgentEvent.RunFailed(e.getMessage()));
            throw e;
        }
    }

    /**
     * 持续进行“模型 -> 工具 -> 模型”循环，直到没有新的工具调用。
     */
    private String continueUntilFinal() throws IOException {
        for (int round = 0; round < maxToolRounds; round++) {
            int roundNumber = round + 1;
            publish(new AgentEvent.TurnStarted(roundNumber));

            // 每次请求 LLM 之前才构造上下文。
            // 这样 ContextBuilder 可以根据当前 token 压力决定是否压缩，
            // 而 SessionStore 仍然保留未压缩的完整对话转写。
            ContextBuildResult context = contextBuilder.build(sessionStore.loadMessages());
            publish(new AgentEvent.ContextBuilt(
                    context.compressed(),
                    context.beforeTokens(),
                    context.afterTokens(),
                    context.maxContextTokens()
            ));
            List<Map<String, Object>> tools = toolRegistry.toLlmToolSchemas();
            BeforeLlmRequestContext hookContext = hookRegistry.apply(AgentHookPoints.BEFORE_LLM_REQUEST, new BeforeLlmRequestContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    roundNumber,
                    model,
                    context.maxContextTokens(),
                    context.messages(),
                    tools
            ));
            List<Message> requestMessages = hookContext.messages();
            List<Map<String, Object>> requestTools = hookContext.tools();
            publish(new AgentEvent.LlmRequestStarted(
                    roundNumber,
                    model,
                    requestMessages.size(),
                    requestTools.size()
            ));

            // ChatRequest 是真正发给模型的输入。
            // 注意：没有工具时不要传 tool_choice=auto，一些 OpenAI-compatible 服务
            // 会要求 tool_choice 只有在工具列表非空时才合法。
            ChatRequest request = ChatRequest.streaming(
                    model,
                    requestMessages,
                    requestTools,
                    requestTools.isEmpty() ? null : "auto",
                    thinkingEnabled,
                    reasoningEffort
            );

            Message assistant = streamAssistantMessage(request, context.maxContextTokens(), roundNumber);
            publish(new AgentEvent.LlmRequestFinished(roundNumber));
            // assistant 消息必须先原样写入历史。
            // 如果它包含 tool_calls，后面的 tool 消息要通过 tool_call_id 与它配对。
            sessionStore.append(assistant);
            publish(new AgentEvent.MessageFinished(roundNumber, "assistant", assistant.hasToolCalls()));

            if (!assistant.hasToolCalls()) {
                // 没有 tool_calls 说明模型已经给出最终自然语言答案，本轮结束。
                publish(new AgentEvent.TurnFinished(roundNumber, "final"));
                publish(new AgentEvent.Done(assistant.content()));
                return assistant.content();
            }

            // 有 tool_calls 时，Agent 负责执行工具，并把每个结果写回 role=tool。
            // 写回后继续下一轮 LLM，让模型基于工具结果生成最终回答。
            executeToolCalls(assistant.toolCalls());
            publish(new AgentEvent.TurnFinished(roundNumber, "tool_calls"));
        }

        throw new IllegalStateException("Agent stopped after max tool rounds: " + maxToolRounds);
    }

    /**
     * 流式读取一轮 assistant 输出，并拼成完整 assistant 消息。
     */
    private Message streamAssistantMessage(ChatRequest request, int maxContextTokens, int round) throws IOException {
        AssistantMessageBuilder assistantBuilder = new AssistantMessageBuilder();
        publish(new AgentEvent.MessageStarted(round, "assistant"));

        streamingChatClient.stream(request, providerEvent -> {
            assistantBuilder.append(providerEvent);
            emitAgentEvent(providerEvent, maxContextTokens);
        });

        return assistantBuilder.build();
    }

    /**
     * 并行执行所有工具调用，并为每个调用写入一条 tool 结果消息。
     */
    private void executeToolCalls(List<ToolCall> toolCalls) throws IOException {
        for (ToolCall call : toolCalls) {
            publish(new AgentEvent.ToolCallStart(
                    call.id(),
                    call.function().name(),
                    call.function().argumentsJson()
            ));
        }

        List<ToolCall> allowedCalls = new ArrayList<>();
        Map<String, ToolResult> blockedResults = new LinkedHashMap<>();
        for (ToolCall call : toolCalls) {
            ToolHookDecision decision = hookRegistry.decide(AgentHookPoints.BEFORE_TOOL_CALL, new BeforeToolCallContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    call
            ));
            if (decision.type() == ToolHookDecisionType.ALLOW) {
                allowedCalls.add(call);
            } else {
                blockedResults.put(
                        call.id(),
                        ToolResult.error(call.id(), renderBlockedToolMessage(decision))
                                .withExecutionMetadata(call.function().name(), 0)
                );
            }
        }

        Iterator<ToolResult> allowedResults = parallelToolExecutor.executeAll(allowedCalls).iterator();

        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            ToolResult result = blockedResults.containsKey(call.id())
                    ? blockedResults.get(call.id())
                    : allowedResults.next();
            BeforeToolResultAppendContext resultContext = hookRegistry.apply(
                    AgentHookPoints.BEFORE_TOOL_RESULT_APPEND,
                    new BeforeToolResultAppendContext(
                            eventBus.sessionName(),
                            eventBus.currentRunId(),
                            call,
                            result,
                            result.renderText()
                    )
            );
            String toolMessageText = resultContext.toolMessageText();

            // 工具结果必须以 role=tool 写入，并保留原始 tool_call_id。
            // 大结果卸载、脱敏、裁剪这类改写已经通过 before_tool_result_append Hook 完成。
            sessionStore.append(Message.tool(result.toolCallId(), toolMessageText));
            publish(new AgentEvent.ToolCallDone(
                    result.toolCallId(),
                    call.function().name(),
                    toolMessageText,
                    !result.error(),
                    result.elapsedMillis()
            ));
        }
    }

    private String renderBlockedToolMessage(ToolHookDecision decision) {
        if (decision.type() == ToolHookDecisionType.PAUSE_FOR_APPROVAL) {
            return "工具调用需要人工审批，当前 MVP 尚未接入审批 UI，已暂停执行。原因：" + decision.reason();
        }
        return "工具调用被 Hook 拒绝执行。原因：" + decision.reason();
    }

    /**
     * 把供应商统一事件转换成 Agent 层事件。
     *
     * <p>ProviderStreamEvent 是 LLM 接入层的输出，AgentEvent 是 Agent 主循环对外的事件协议。
     * 这层转换让 AgentLoop 不需要知道 OpenAI choices/delta、DeepSeek reasoning_content
     * 这些供应商原始字段。</p>
     */
    private void emitAgentEvent(ProviderStreamEvent event, int maxContextTokens) {
        if (event instanceof ProviderStreamEvent.TextDelta delta) {
            publish(new AgentEvent.AssistantToken(delta.text()));
            return;
        }
        if (event instanceof ProviderStreamEvent.ReasoningDelta delta) {
            publish(new AgentEvent.ReasoningToken(delta.text()));
            return;
        }
        if (event instanceof ProviderStreamEvent.UsageDelta usage) {
            publish(new AgentEvent.UsageReported(usage.usage(), maxContextTokens));
        }
    }

    private void publish(AgentEvent event) {
        eventBus.publish(event);
    }
}
