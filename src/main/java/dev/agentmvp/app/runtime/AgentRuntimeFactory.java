package dev.agentmvp.app.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.core.event.AgentEventHandler;
import dev.agentmvp.core.event.AgentEventBus;
import dev.agentmvp.core.agent.AgentLoop;
import dev.agentmvp.app.background.BackgroundTaskEventBus;
import dev.agentmvp.app.background.BackgroundTaskExecutor;
import dev.agentmvp.app.background.BackgroundTaskHandler;
import dev.agentmvp.app.background.BackgroundTaskManager;
import dev.agentmvp.app.background.BackgroundTaskNotificationHandler;
import dev.agentmvp.app.background.BackgroundTaskScheduler;
import dev.agentmvp.app.background.BackgroundTaskStore;
import dev.agentmvp.app.background.JsonlBackgroundTaskStore;
import dev.agentmvp.app.background.NoopTaskHandler;
import dev.agentmvp.core.context.ContextBuilder;
import dev.agentmvp.core.context.ContextPipeline;
import dev.agentmvp.core.context.SimpleTokenEstimator;
import dev.agentmvp.core.context.TranscriptSummarizer;
import dev.agentmvp.core.context.model.ContextOptions;
import dev.agentmvp.core.hook.AgentHookPoints;
import dev.agentmvp.core.hook.HookRegistry;
import dev.agentmvp.llm.OpenAiCompatibleChatClient;
import dev.agentmvp.llm.OpenAiCompatibleProviderFactory;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.app.memory.LongTermMemoryInjectHook;
import dev.agentmvp.app.memory.MarkdownMemoryStore;
import dev.agentmvp.app.memory.MemoryExtractionAgent;
import dev.agentmvp.app.memory.MemoryExtractionHook;
import dev.agentmvp.app.memory.MemoryExtractionTaskHandler;
import dev.agentmvp.app.memory.MemoryPromptRenderer;
import dev.agentmvp.app.mcp.McpToolLoader;
import dev.agentmvp.app.mcp.config.McpClientFactory;
import dev.agentmvp.app.mcp.config.McpConfigLoader;
import dev.agentmvp.app.mcp.config.model.McpConfig;
import dev.agentmvp.app.mcp.config.model.McpServerConfig;
import dev.agentmvp.app.mcp.McpToolExecutor;
import dev.agentmvp.app.notification.NotificationSink;
import dev.agentmvp.app.prompt.PromptLoader;
import dev.agentmvp.app.prompt.PromptPaths;
import dev.agentmvp.core.session.BootstrappedSessionStore;
import dev.agentmvp.core.session.JsonlSessionStore;
import dev.agentmvp.core.session.SessionCatalog;
import dev.agentmvp.core.session.SessionStore;
import dev.agentmvp.app.skill.SkillIndexRenderer;
import dev.agentmvp.app.skill.SkillRepository;
import dev.agentmvp.core.tool.LocalToolExecutor;
import dev.agentmvp.core.tool.ParallelToolExecutor;
import dev.agentmvp.core.tool.ToolRegistry;
import dev.agentmvp.app.tool.builtin.BuiltinTools;
import dev.agentmvp.app.tool.result.ToolResultOffloadHook;
import dev.agentmvp.app.tool.result.ToolResultOffloader;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 创建 Agent 运行时。
 *
 * <p>入口层只负责 UI。Prompt、Skill、Tool、LLM、ContextBuilder 的装配集中在这里，
 * 避免 TUI 里混入一大段和界面无关的初始化代码。</p>
 */
public class AgentRuntimeFactory {
    /**
     * 单次用户请求内允许的最大工具循环轮数。
     *
     * <p>这里不是聊天 session 的总轮数，而是一次 run() 里
     * “LLM -> tool_calls -> tool results -> LLM”的最大循环次数。</p>
     */
    private static final int MAX_TOOL_ROUNDS = 50;

    /**
     * 使用默认配置创建 Agent 运行时。
     */
    public AgentRuntime create(AgentEventHandler eventHandler) throws IOException {
        return create(eventHandler, NotificationSink.noop(), SessionCatalog.DEFAULT_SESSION);
    }

    /**
     * 使用指定 session 创建 Agent 运行时。
     */
    public AgentRuntime create(AgentEventHandler eventHandler, String sessionName) throws IOException {
        return create(eventHandler, NotificationSink.noop(), sessionName);
    }

    /**
     * 使用指定 session 和通知出口创建 Agent 运行时。
     */
    public AgentRuntime create(AgentEventHandler eventHandler, NotificationSink notificationSink, String sessionName) throws IOException {
        Objects.requireNonNull(eventHandler);
        Objects.requireNonNull(notificationSink);
        SessionCatalog.requireValidName(sessionName);

        OpenAiCompatibleProvider provider = OpenAiCompatibleProviderFactory.fromEnvWithDeepSeekDefaults();
        if (provider.apiKey() == null || provider.apiKey().isBlank()) {
            throw new IllegalStateException("Missing API key. Set DEEPSEEK_API_KEY or OPENAI_COMPATIBLE_API_KEY.");
        }
        WorkspacePaths.ensureDirectories();

        ObjectMapper objectMapper = new ObjectMapper();
        PromptLoader promptLoader = new PromptLoader();
        String systemPrompt = promptLoader.load(PromptPaths.SYSTEM);
        String contextSummaryPrompt = promptLoader.load(PromptPaths.CONTEXT_SUMMARY);
        String longTermMemorySystemPrompt = promptLoader.load(PromptPaths.LONG_TERM_MEMORY_SYSTEM);
        String memoryExtractionPrompt = promptLoader.load(PromptPaths.MEMORY_EXTRACTION);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        StreamingChatClient streamingChatClient = OpenAiCompatibleChatClient.create(httpClient, objectMapper, provider);
        LocalToolExecutor localToolExecutor = new LocalToolExecutor(objectMapper);
        McpToolExecutor mcpToolExecutor = new McpToolExecutor();
        ToolRegistry toolRegistry = new ToolRegistry(localToolExecutor, mcpToolExecutor);

        SkillRepository skillRepository = SkillRepository.scan(WorkspacePaths.SKILLS);
        // 所有本地内置工具最终都通过 ToolRegistry.registerLocal 注册。
        BuiltinTools.registerAll(toolRegistry, Path.of("."), skillRepository);
        loadConfiguredMcpServers(objectMapper, httpClient, toolRegistry, mcpToolExecutor);
        ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4);
        MarkdownMemoryStore memoryStore = new MarkdownMemoryStore(WorkspacePaths.LONG_TERM_MEMORY);
        MemoryPromptRenderer memoryPromptRenderer = new MemoryPromptRenderer(longTermMemorySystemPrompt);

        List<Message> bootstrapMessages = new ArrayList<>();
        // 基础 system prompt 来自 jar 内置 resources/prompts/system.md。
        bootstrapMessages.add(Message.system(systemPrompt));

        String skillIndex = new SkillIndexRenderer().render(skillRepository.listMetadata());
        if (!skillIndex.isBlank()) {
            // Skill 索引只包含 name/description；完整 SKILL.md 由 load_skill 按需加载。
            bootstrapMessages.add(Message.system(skillIndex));
        }
        SessionStore sessionStore = new BootstrappedSessionStore(
                bootstrapMessages,
                JsonlSessionStore.openNamed(objectMapper, WorkspacePaths.SESSIONS, sessionName)
        );
        MemoryExtractionAgent memoryExtractionAgent = new MemoryExtractionAgent(
                provider.defaultModel(),
                sessionStore,
                memoryStore,
                streamingChatClient,
                objectMapper,
                memoryExtractionPrompt
        );
        BackgroundTaskManager backgroundTaskManager = createBackgroundTaskManager(
                objectMapper,
                notificationSink,
                List.of(
                        new NoopTaskHandler(),
                        new MemoryExtractionTaskHandler(memoryExtractionAgent)
                )
        );
        backgroundTaskManager.start();
        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.register(
                AgentHookPoints.BEFORE_LLM_REQUEST,
                new LongTermMemoryInjectHook(memoryStore, memoryPromptRenderer)
        );
        hookRegistry.register(
                AgentHookPoints.BEFORE_TOOL_RESULT_APPEND,
                new ToolResultOffloadHook(ToolResultOffloader.defaults(objectMapper, WorkspacePaths.TOOL_RESULTS))
        );
        hookRegistry.register(
                AgentHookPoints.AFTER_RUN,
                new MemoryExtractionHook(backgroundTaskManager)
        );
        AgentEventBus eventBus = AgentEventBus.single(sessionName, eventHandler);
        ContextPipeline contextPipeline = new ContextPipeline(
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(contextSummaryPrompt, 8_000),
                        ContextOptions.defaults()
                )
        );

        AgentLoop agentLoop = new AgentLoop(
                provider,
                sessionStore,
                contextPipeline,
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                hookRegistry,
                eventBus,
                MAX_TOOL_ROUNDS
        );

        return new AgentRuntime(
                agentLoop,
                backgroundTaskManager,
                parallelToolExecutor,
                mcpToolExecutor,
                provider,
                sessionName,
                skillRepository.listMetadata().size()
        );
    }

    /**
     * 创建后台任务框架。
     *
     * <p>后台框架只负责调度、执行记录和事件通知。
     * 具体动作由 handlers 决定，例如 noop 或长期记忆抽取。</p>
     */
    private BackgroundTaskManager createBackgroundTaskManager(
            ObjectMapper objectMapper,
            NotificationSink notificationSink,
            List<BackgroundTaskHandler> handlers
    ) throws IOException {
        BackgroundTaskStore store = new JsonlBackgroundTaskStore(
                objectMapper,
                WorkspacePaths.BACKGROUND_TASKS,
                WorkspacePaths.BACKGROUND_TASK_RUNS
        );
        BackgroundTaskEventBus eventBus = BackgroundTaskEventBus.single(
                new BackgroundTaskNotificationHandler(notificationSink)
        );
        BackgroundTaskExecutor executor = new BackgroundTaskExecutor(
                store,
                handlers,
                eventBus
        );
        return new BackgroundTaskManager(
                store,
                new BackgroundTaskScheduler(executor)
        );
    }

    /**
     * 从 workspace/mcp.json 加载外部 MCP Server。
     *
     * <p>没有 workspace/mcp.json 时什么都不做；有配置时，每个 server 都会经历：
     * 创建客户端 -> initialize -> tools/list -> 注册成普通 Tool。</p>
     */
    private void loadConfiguredMcpServers(
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            ToolRegistry toolRegistry,
            McpToolExecutor mcpToolExecutor
    ) throws IOException {
        McpConfig mcpConfig = new McpConfigLoader(objectMapper).loadIfExists(WorkspacePaths.MCP_CONFIG);
        McpClientFactory clientFactory = new McpClientFactory(httpClient, objectMapper);
        McpToolLoader toolLoader = new McpToolLoader(toolRegistry, mcpToolExecutor);

        for (McpServerConfig serverConfig : mcpConfig.servers()) {
            try {
                toolLoader.load(clientFactory.create(serverConfig));
            } catch (IOException e) {
                // 如果前面的 MCP 已经注册成功，当前 MCP 又加载失败，
                // 启动会终止，因此要先释放已经启动的本地 MCP 子进程。
                mcpToolExecutor.close();
                // 配了 MCP 却启动失败，直接让启动失败，比静默丢工具更容易排查。
                throw new IOException("Failed to load MCP server " + serverConfig.id(), e);
            }
        }
    }
}
