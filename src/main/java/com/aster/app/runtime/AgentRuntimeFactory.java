package com.aster.app.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.core.event.AgentEventHandler;
import com.aster.core.event.AgentEventBus;
import com.aster.core.agent.AgentLoop;
import com.aster.app.background.BackgroundTaskEventBus;
import com.aster.app.background.BackgroundTaskExecutor;
import com.aster.app.background.BackgroundTaskHandler;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.BackgroundTaskNotificationHandler;
import com.aster.app.background.BackgroundTaskScheduler;
import com.aster.app.background.BackgroundTaskStore;
import com.aster.app.background.JsonlBackgroundTaskStore;
import com.aster.app.background.NoopTaskHandler;
import com.aster.app.extension.RuntimeExtensionContext;
import com.aster.app.extension.RuntimeExtensionRegistry;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.ContextPipeline;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.TranscriptSummarizer;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.hook.HookRegistry;
import com.aster.llm.OpenAiCompatibleChatClient;
import com.aster.llm.OpenAiCompatibleProviderFactory;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.Message;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryExtractionAgent;
import com.aster.app.memory.MemoryExtractionTaskHandler;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.notification.NotificationSink;
import com.aster.app.prompt.PromptLoader;
import com.aster.app.prompt.PromptPaths;
import com.aster.core.session.BootstrappedSessionStore;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionCatalog;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.SessionStore;
import com.aster.app.skill.SkillRepository;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.app.tool.builtin.BuiltinTools;
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
        // sessionId 是 JSONL 文件名；displayName 只保存在 index.json，默认沿用 id。
        new SessionIndex(objectMapper, WorkspacePaths.SESSIONS).ensure(sessionName, sessionName);
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
        // read/write/bash/edit 是固定底座工具；load_skill、MCP 等能力走 RuntimeExtension。
        BuiltinTools.registerAll(toolRegistry, Path.of("."));
        MarkdownMemoryStore memoryStore = new MarkdownMemoryStore(WorkspacePaths.LONG_TERM_MEMORY);
        MemoryPromptRenderer memoryPromptRenderer = new MemoryPromptRenderer(longTermMemorySystemPrompt);

        List<Message> bootstrapMessages = new ArrayList<>();
        // 基础 system prompt 来自 jar 内置 resources/prompts/system.md。
        bootstrapMessages.add(Message.system(systemPrompt));

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

        RuntimeExtensionContext extensionContext = new RuntimeExtensionContext(
                objectMapper,
                httpClient,
                provider,
                streamingChatClient,
                sessionStore,
                toolRegistry,
                hookRegistry,
                mcpToolExecutor,
                skillRepository,
                memoryStore,
                memoryPromptRenderer,
                backgroundTaskManager
        );
        RuntimeExtensionRegistry extensionRegistry = RuntimeExtensionRegistry.defaults();
        extensionRegistry.registerTools(extensionContext);
        extensionRegistry.registerHooks(extensionContext);
        ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4);

        List<AgentEventHandler> eventHandlers = new ArrayList<>();
        eventHandlers.add(eventHandler);
        eventHandlers.addAll(extensionRegistry.eventHandlers(extensionContext));
        AgentEventBus eventBus = new AgentEventBus(sessionName, eventHandlers);
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
        AgentRunCoordinator runCoordinator = new AgentRunCoordinator(agentLoop, eventBus);

        return new AgentRuntime(
                agentLoop,
                runCoordinator,
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

}
