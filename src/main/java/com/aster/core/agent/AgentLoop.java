package com.aster.core.agent;

import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.agent.control.AgentRunControl;
import com.aster.core.agent.control.AgentStopException;
import com.aster.llm.model.Message;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.llm.model.ToolCall;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.ContextPipeline;
import com.aster.core.context.model.ContextBuildResult;
import com.aster.core.hook.AgentHookPoints;
import com.aster.core.hook.AfterRunContext;
import com.aster.core.hook.BeforeLlmRequestContext;
import com.aster.core.hook.BeforeToolCallContext;
import com.aster.core.hook.BeforeToolResultAppendContext;
import com.aster.core.hook.HookRegistry;
import com.aster.core.hook.ToolHookDecision;
import com.aster.core.hook.ToolHookDecisionType;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.ChatRequest;
import com.aster.llm.model.ProviderStreamEvent;
import com.aster.core.session.SessionStore;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.ToolResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Agent 的流式主循环。
 *
 * <p>这个项目只保留一条 LLM 调用路径：SSE 流式调用。
 * 主循环一边接收 assistant 增量，一边拼回完整 assistant 消息；
 * 如果模型请求工具，就并行执行工具，再把结果写回会话，继续请求下一轮模型。</p>
 */
public class AgentLoop {
    private final Supplier<String> modelSupplier;
    private final SessionStore sessionStore;
    private final ContextPipeline contextPipeline;
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
                () -> model,
                sessionStore,
                new ContextPipeline(sessionStore, contextBuilder),
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
                () -> provider.defaultModel(),
                sessionStore,
                new ContextPipeline(sessionStore, contextBuilder),
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
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                () -> provider.defaultModel(),
                sessionStore,
                new ContextPipeline(sessionStore, contextBuilder),
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

    public AgentLoop(
            OpenAiCompatibleProvider provider,
            SessionStore sessionStore,
            ContextPipeline contextPipeline,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                () -> provider.defaultModel(),
                sessionStore,
                contextPipeline,
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

    /**
     * 创建支持动态模型读取的 AgentLoop，并沿用现有 ContextBuilder 构造上下文窗口。
     *
     * <p>Team、Plan Worker、Room Agent 需要在运行时固定或读取不同模型，
     * 但它们仍然复用原来的 ContextBuilder 配置。</p>
     */
    public AgentLoop(
            Supplier<String> modelSupplier,
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
                modelSupplier,
                provider,
                sessionStore,
                new ContextPipeline(sessionStore, contextBuilder),
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                hookRegistry,
                eventBus,
                maxToolRounds
        );
    }

    /**
     * 创建支持动态模型读取的 AgentLoop。
     *
     * <p>供应商仍提供 thinking 等能力开关，modelSupplier 只负责返回当前 chat 模型。</p>
     */
    public AgentLoop(
            Supplier<String> modelSupplier,
            OpenAiCompatibleProvider provider,
            SessionStore sessionStore,
            ContextPipeline contextPipeline,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
            int maxToolRounds
    ) {
        this(
                modelSupplier,
                sessionStore,
                contextPipeline,
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
            Supplier<String> modelSupplier,
            SessionStore sessionStore,
            ContextPipeline contextPipeline,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            ParallelToolExecutor parallelToolExecutor,
            HookRegistry hookRegistry,
            AgentEventBus eventBus,
            int maxToolRounds,
            boolean thinkingEnabled,
            String reasoningEffort
    ) {
        this.modelSupplier = Objects.requireNonNull(modelSupplier);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.contextPipeline = Objects.requireNonNull(contextPipeline);
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
     * 执行一次用户请求，直到模型返回最终答案。
     */
    public String run(String userInput) throws IOException {
        return run(userInput, new AgentRunControl());
    }

    /**
     * 使用指定控制信号执行一次用户请求。
     *
     * <p>control 只影响当前 run：运行中引导会在 LLM 请求前由 Hook 消费，
     * stop 请求会在 AgentLoop 的安全点转成用户主动停止。</p>
     */
    public String run(String userInput, AgentRunControl control) throws IOException {
        Objects.requireNonNull(control);
        eventBus.beginRun();
        publish(new AgentEvent.RunStarted(userInput));
        sessionStore.recordRunStarted(userInput);
        try {
            // SessionStore 保存的是完整原始会话。这里不要先压缩再保存，
            // 否则 debug、恢复会话、后续重新压缩都会丢失真实历史。
            publish(new AgentEvent.MessageStarted(0, "user"));
            sessionStore.append(Message.user(userInput));
            publish(new AgentEvent.MessageFinished(0, "user", false));
            String answer = continueUntilFinal(control);
            sessionStore.recordRunFinished(answer);
            publish(new AgentEvent.RunFinished(answer));
            hookRegistry.fireQuietly(AgentHookPoints.AFTER_RUN, new AfterRunContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    userInput,
                    answer
            ));
            return answer;
        } catch (AgentStopException e) {
            String answer = e.partialText();
            sessionStore.recordRunFinished(answer);
            publish(new AgentEvent.RunStopped(answer));
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
    private String continueUntilFinal(AgentRunControl control) throws IOException {
        for (int round = 0; round < maxToolRounds; round++) {
            int roundNumber = round + 1;
            checkStop(control);
            publish(new AgentEvent.TurnStarted(roundNumber));

            // 每次请求 LLM 之前都必须经过内置 ContextPipeline。
            // 这里不是普通 Hook，而是必经 Stage：读取完整 session、压缩上下文、
            // 校验 tool_call/tool_result 协议。
            ContextBuildResult context = contextPipeline.build();
            publish(new AgentEvent.ContextBuilt(
                    context.compressed(),
                    context.beforeTokens(),
                    context.afterTokens(),
                    context.maxContextTokens()
            ));
            checkStop(control);
            List<Map<String, Object>> tools = toolRegistry.toLlmToolSchemas();
            String model = currentModel();
            BeforeLlmRequestContext hookContext = hookRegistry.apply(AgentHookPoints.BEFORE_LLM_REQUEST, new BeforeLlmRequestContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    roundNumber,
                    model,
                    context.maxContextTokens(),
                    context.summary(),
                    control,
                    context.messages(),
                    tools
            ));
            List<Message> requestMessages = hookContext.messages();
            List<Map<String, Object>> requestTools = hookContext.tools();
            checkStop(control);
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

            Message assistant;
            try {
                assistant = streamAssistantMessage(request, context.maxContextTokens(), roundNumber, control);
            } catch (AgentStopException e) {
                publish(new AgentEvent.LlmRequestFinished(roundNumber));
                Message partialAssistant = e.partialAssistant();
                if (partialAssistant != null) {
                    sessionStore.append(partialAssistant);
                    publish(new AgentEvent.MessageFinished(roundNumber, "assistant", false));
                }
                publish(new AgentEvent.TurnFinished(roundNumber, "stopped"));
                publish(new AgentEvent.Done(e.partialText()));
                throw e;
            }
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
            if (control.stopRequested()) {
                publish(new AgentEvent.TurnFinished(roundNumber, "stopped"));
                throw new AgentStopException();
            }
            publish(new AgentEvent.TurnFinished(roundNumber, "tool_calls"));
        }

        throw new IllegalStateException("Agent stopped after max tool rounds: " + maxToolRounds);
    }

    /**
     * 读取当前 chat 模型。
     *
     * <p>主 runtime 可以在运行中切换模型。这里在每次 LLM 请求前读取一次，
     * 因此已经发出的 SSE 请求不会被打断，下一次请求会使用新模型。</p>
     */
    private String currentModel() {
        String model = modelSupplier.get();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("chat model is blank");
        }
        return model.trim();
    }

    /**
     * 流式读取一轮 assistant 输出，并拼成完整 assistant 消息。
     */
    private Message streamAssistantMessage(
            ChatRequest request,
            int maxContextTokens,
            int round,
            AgentRunControl control
    ) throws IOException {
        AssistantMessageBuilder assistantBuilder = new AssistantMessageBuilder();
        publish(new AgentEvent.MessageStarted(round, "assistant"));

        streamingChatClient.stream(request, providerEvent -> {
            assistantBuilder.append(providerEvent);
            emitAgentEvent(providerEvent, maxContextTokens);
            if (control.stopRequested()) {
                throw new AgentStopException(assistantBuilder.buildPartialTextMessage());
            }
        });

        return assistantBuilder.build();
    }

    /**
     * 并行执行所有工具调用，并为每个调用写入一条 tool 结果消息。
     */
    private void executeToolCalls(List<ToolCall> toolCalls) throws IOException {
        List<ToolCall> allowedCalls = new ArrayList<>();
        Map<String, ToolResult> blockedResults = new LinkedHashMap<>();
        for (ToolCall call : toolCalls) {
            ToolHookDecision decision = hookRegistry.decide(AgentHookPoints.BEFORE_TOOL_CALL, new BeforeToolCallContext(
                    eventBus.sessionName(),
                    eventBus.currentRunId(),
                    call
            ));
            if (decision.type() == ToolHookDecisionType.ALLOW) {
                publish(new AgentEvent.ToolCallStart(
                        call.id(),
                        call.function().name(),
                        call.function().argumentsJson()
                ));
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

    /**
     * 在不会破坏工具协议的位置检查用户停止请求。
     */
    private void checkStop(AgentRunControl control) {
        if (control.stopRequested()) {
            throw new AgentStopException();
        }
    }
}
