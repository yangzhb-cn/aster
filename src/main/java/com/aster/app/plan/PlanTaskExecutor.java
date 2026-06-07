package com.aster.app.plan;

import com.aster.app.plan.model.ExecutionPlan;
import com.aster.app.plan.model.PlanTask;
import com.aster.app.runtime.AgentEventPublisher;
import com.aster.core.agent.AgentLoop;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.TranscriptSummarizer;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.event.AgentEventBus;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.BootstrappedSessionStore;
import com.aster.core.session.InMemorySessionStore;
import com.aster.core.session.SessionStore;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.Message;
import com.aster.llm.model.OpenAiCompatibleProvider;

import java.util.Objects;

/**
 * 用临时子 Agent 执行 Plan DAG 中的单个节点。
 *
 * <p>子 Agent 使用内存 session，不写入主会话；工具和 Hook 复用当前 runtime，
 * 因此 write/edit/bash 仍然会走现有 HITL 审批。</p>
 */
public class PlanTaskExecutor {
    private static final int MAX_TOOL_ROUNDS = 100;

    private final OpenAiCompatibleProvider provider;
    private final StreamingChatClient streamingChatClient;
    private final ToolRegistry toolRegistry;
    private final HookRegistry hookRegistry;
    private final AgentEventPublisher eventPublisher;
    private final String systemPrompt;
    private final String model;

    public PlanTaskExecutor(
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient,
            ToolRegistry toolRegistry,
            HookRegistry hookRegistry,
            AgentEventPublisher eventPublisher,
            String systemPrompt,
            String model
    ) {
        this.provider = Objects.requireNonNull(provider);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.hookRegistry = Objects.requireNonNull(hookRegistry);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.systemPrompt = Objects.requireNonNull(systemPrompt);
        this.model = requireText(model, "model");
    }

    /**
     * 执行一个 Plan 节点，并把开始/完成状态发布给 UI。
     */
    public String execute(PlanTask task, ExecutionPlan plan) throws Exception {
        long start = System.nanoTime();
        eventPublisher.publish(new AgentEvent.PlanTaskStarted(
                task.id(),
                task.type().name(),
                task.description()
        ));
        try {
            String output = runTaskAgent(task, plan);
            eventPublisher.publish(new AgentEvent.PlanTaskFinished(
                    task.id(),
                    true,
                    output,
                    elapsedMillis(start)
            ));
            return output;
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            eventPublisher.publish(new AgentEvent.PlanTaskFinished(
                    task.id(),
                    false,
                    error,
                    elapsedMillis(start)
            ));
            throw e;
        }
    }

    private String runTaskAgent(PlanTask task, ExecutionPlan plan) throws Exception {
        SessionStore sessionStore = new BootstrappedSessionStore(
                java.util.List.of(Message.system(systemPrompt)),
                new InMemorySessionStore()
        );
        try (ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4)) {
            AgentLoop agentLoop = new AgentLoop(
                    () -> model,
                    provider,
                    sessionStore,
                    new ContextBuilder(
                            new SimpleTokenEstimator(),
                            new TranscriptSummarizer(4_000),
                            ContextOptions.defaults()
                    ),
                    streamingChatClient,
                    toolRegistry,
                    parallelToolExecutor,
                    hookRegistry,
                    planTaskEventBus(task),
                    MAX_TOOL_ROUNDS
            );
            return agentLoop.run(taskPrompt(task, plan));
        }
    }

    private AgentEventBus planTaskEventBus(PlanTask task) {
        return AgentEventBus.single("plan-" + task.id(), envelope -> {
            AgentEvent event = envelope.event();
            if (event instanceof AgentEvent.ToolCallStart tool) {
                eventPublisher.publish(new AgentEvent.ToolCallStart(
                        tool.toolCallId(),
                        planToolName(task, tool.toolName()),
                        tool.argumentsJson()
                ));
                return;
            }
            if (event instanceof AgentEvent.ToolCallDone tool) {
                eventPublisher.publish(new AgentEvent.ToolCallDone(
                        tool.toolCallId(),
                        planToolName(task, tool.toolName()),
                        tool.text(),
                        tool.success(),
                        tool.elapsedMillis()
                ));
            }
        });
    }

    private String taskPrompt(PlanTask task, ExecutionPlan plan) {
        return """
                原始目标：
                %s

                当前 DAG 节点：
                - id: %s
                - type: %s
                - description: %s

                已完成依赖结果：
                %s

                请只完成当前节点。需要读取、搜索、修改或验证时使用工具。
                最终用中文输出当前节点的执行结果、关键证据和后续影响。
                """.formatted(
                plan.task(),
                task.id(),
                task.type(),
                task.description(),
                dependencyResults(task, plan)
        ).strip();
    }

    private String dependencyResults(PlanTask task, ExecutionPlan plan) {
        if (task.dependencies().isEmpty()) {
            return "无";
        }
        StringBuilder output = new StringBuilder();
        for (String dependencyId : task.dependencies()) {
            PlanTask dependency = plan.task(dependencyId);
            output.append("- ").append(dependencyId).append(": ")
                    .append(dependency == null ? "" : dependency.result())
                    .append('\n');
        }
        return output.toString().stripTrailing();
    }

    private String planToolName(PlanTask task, String toolName) {
        return "plan(" + task.id() + ")." + toolName;
    }

    private long elapsedMillis(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1_000_000);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
