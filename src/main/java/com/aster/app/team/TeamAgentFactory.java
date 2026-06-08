package com.aster.app.team;

import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.runtime.AgentEventPublisher;
import com.aster.app.tool.builtin.ReadTool;
import com.aster.app.tool.developer.GlobTool;
import com.aster.app.tool.developer.GrepTool;
import com.aster.app.tool.developer.LsTool;
import com.aster.app.tool.developer.WebFetchTool;
import com.aster.app.tool.developer.WebSearchTool;
import com.aster.app.team.model.TeamPromptSet;
import com.aster.app.team.model.TeamRole;
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
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 创建探索 Team 的只读子 Agent。
 *
 * <p>子 Agent 使用内存 session，不写入主会话；工具集只包含 read、ls、glob、grep、
 * web_fetch、web_search，避免探索阶段修改文件或递归启动更多 Agent。</p>
 */
public class TeamAgentFactory {
    private static final int MAX_TOOL_ROUNDS = 100;

    private final Path workingDirectory;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final OpenAiCompatibleProvider provider;
    private final StreamingChatClient streamingChatClient;
    private final TeamPromptSet promptSet;

    public TeamAgentFactory(
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient,
            TeamPromptSet promptSet
    ) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.provider = Objects.requireNonNull(provider);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
        this.promptSet = Objects.requireNonNull(promptSet);
    }

    /**
     * 执行一个 Team 成员任务。
     */
    public String run(TeamRole role, String taskId, String prompt, String model, AgentEventPublisher eventPublisher) throws Exception {
        String selectedModel = requireModel(model);
        ToolRegistry toolRegistry = readonlyToolRegistry();
        SessionStore sessionStore = new BootstrappedSessionStore(
                List.of(Message.system(promptSet.systemPrompt(role))),
                new InMemorySessionStore()
        );
        try (ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4)) {
            AgentLoop agentLoop = new AgentLoop(
                    () -> selectedModel,
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
                    HookRegistry.empty(),
                    teamEventBus(role, taskId, eventPublisher),
                    MAX_TOOL_ROUNDS
            );
            return agentLoop.run(prompt);
        }
    }

    private String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("team model is required");
        }
        return model.trim();
    }

    private AgentEventBus teamEventBus(TeamRole role, String taskId, AgentEventPublisher eventPublisher) {
        return AgentEventBus.single("team-" + role.id(), envelope -> {
            AgentEvent event = envelope.event();
            // Team 子 Agent 工具调用数量很多，只转发成员正文，避免 UI 被 read/grep 事件刷屏。
            if (event instanceof AgentEvent.AssistantToken token) {
                eventPublisher.publish(new AgentEvent.TeamMemberToken(taskId, role.id(), token.text()));
            }
        });
    }

    private ToolRegistry readonlyToolRegistry() {
        ToolRegistry toolRegistry = new ToolRegistry(new LocalToolExecutor(objectMapper), new McpToolExecutor());
        new ReadTool(workingDirectory).registerTo(toolRegistry);
        new LsTool(workingDirectory).registerTo(toolRegistry);
        new GlobTool(workingDirectory).registerTo(toolRegistry);
        new GrepTool(workingDirectory).registerTo(toolRegistry);
        new WebFetchTool(workingDirectory, httpClient).registerTo(toolRegistry);
        new WebSearchTool(workingDirectory, objectMapper, httpClient).registerTo(toolRegistry);
        return toolRegistry;
    }

}
