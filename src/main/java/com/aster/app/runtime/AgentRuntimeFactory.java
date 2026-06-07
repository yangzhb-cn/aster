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
import com.aster.app.background.ReminderTaskHandler;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskAction;
import com.aster.app.background.model.TaskStatus;
import com.aster.app.background.model.TaskTrigger;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.extension.RuntimeExtensionContext;
import com.aster.app.extension.RuntimeExtensionRegistry;
import com.aster.core.context.ContextPipeline;
import com.aster.core.context.ContextWindowCache;
import com.aster.core.context.LlmSummarizer;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.Summarizer;
import com.aster.core.context.TokenEstimator;
import com.aster.core.context.TranscriptSummarizer;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.context.model.ContextWindowSnapshot;
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
import com.aster.app.room.JsonRoomAgentRegistry;
import com.aster.app.room.JsonRoomMembershipStore;
import com.aster.app.room.JsonRoomStore;
import com.aster.app.room.JsonlRoomMessageStore;
import com.aster.app.room.RoomAgentPromptStore;
import com.aster.app.room.RoomAgentRegistry;
import com.aster.app.room.RoomAgentRunner;
import com.aster.app.room.RoomAgentSessionFactory;
import com.aster.app.room.RoomAgentSessionCleaner;
import com.aster.app.room.RoomAgentTemplateSeeder;
import com.aster.app.room.RoomCoordinator;
import com.aster.app.room.RoomHub;
import com.aster.app.room.RoomMentionParser;
import com.aster.app.room.RoomMembershipStore;
import com.aster.app.room.RoomPromptBuilder;
import com.aster.app.room.RoomStore;
import com.aster.app.room.RoomToolRegistryFactory;
import com.aster.app.schedule.JsonScheduledUserMessageStore;
import com.aster.app.schedule.ScheduledUserMessageManager;
import com.aster.app.schedule.ScheduledUserMessageStore;
import com.aster.core.session.JsonlSessionStore;
import com.aster.core.session.SessionReplayer;
import com.aster.core.session.SessionCatalog;
import com.aster.core.session.SessionIndex;
import com.aster.core.session.SessionStore;
import com.aster.core.session.model.SessionMessageRecord;
import com.aster.core.session.model.SessionReplayResult;
import com.aster.app.skill.SkillRepository;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.app.tool.builtin.BuiltinTools;
import com.aster.app.todo.JsonTodoStore;
import com.aster.app.todo.TodoScanTaskHandler;
import com.aster.app.todo.TodoStore;
import com.aster.app.plan.PlanPlannerAgent;
import com.aster.app.plan.PlanTaskExecutor;
import com.aster.app.team.AgentTeamRunner;
import com.aster.app.team.TeamAgentFactory;
import com.aster.app.team.TeamTaskExecutor;
import com.aster.app.team.model.TeamPromptSet;
import com.aster.llm.DeepSeekModels;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final int MAX_TOOL_ROUNDS = 100;
    private static final String CONTEXT_SUMMARIZER_ID = "llm";

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
        List<String> availableChatModels = availableChatModels(provider);
        AtomicReference<String> chatModel = new AtomicReference<>(initialChatModel(provider, availableChatModels));
        WorkspacePaths.ensureDirectories();

        ObjectMapper objectMapper = new ObjectMapper();
        // sessionId 是 JSONL 文件名；displayName 只保存在 index.json，默认沿用 id。
        new SessionIndex(objectMapper, WorkspacePaths.SESSIONS).ensure(sessionName, sessionName);
        PromptLoader promptLoader = new PromptLoader();
        String systemPrompt = promptLoader.load(PromptPaths.SYSTEM);
        String contextSummaryPrompt = promptLoader.load(PromptPaths.CONTEXT_SUMMARY);
        String longTermMemorySystemPrompt = promptLoader.load(PromptPaths.LONG_TERM_MEMORY_SYSTEM);
        String memoryExtractionPrompt = promptLoader.load(PromptPaths.MEMORY_EXTRACTION);
        TeamPromptSet teamPromptSet = new TeamPromptSet(
                promptLoader.load(PromptPaths.TEAM_PLANNER_SYSTEM),
                promptLoader.load(PromptPaths.TEAM_CODE_RESEARCHER_SYSTEM),
                promptLoader.load(PromptPaths.TEAM_RISK_REVIEWER_SYSTEM)
        );
        String teamFinalSummaryUserPrompt = promptLoader.load(PromptPaths.TEAM_FINAL_SUMMARY_USER);
        String planPlannerSystemPrompt = promptLoader.load(PromptPaths.PLAN_PLANNER_SYSTEM);
        String planTaskExecutorSystemPrompt = promptLoader.load(PromptPaths.PLAN_TASK_EXECUTOR_SYSTEM);
        String planFinalSummaryUserPrompt = promptLoader.load(PromptPaths.PLAN_FINAL_SUMMARY_USER);
        String roomAgentWrapperSystemPrompt = promptLoader.load(PromptPaths.ROOM_AGENT_WRAPPER_SYSTEM);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        StreamingChatClient streamingChatClient = OpenAiCompatibleChatClient.create(httpClient, objectMapper, provider);
        LocalToolExecutor localToolExecutor = new LocalToolExecutor(objectMapper);
        McpToolExecutor mcpToolExecutor = new McpToolExecutor();
        ToolRegistry toolRegistry = new ToolRegistry(localToolExecutor, mcpToolExecutor);
        AgentEventPublisher eventPublisher = new AgentEventPublisher();

        SkillRepository skillRepository = SkillRepository.scan(WorkspacePaths.SKILLS);
        // read/write/bash/edit 是固定底座工具；load_skill、MCP 等能力走 RuntimeExtension。
        BuiltinTools.registerAll(toolRegistry, Path.of("."));
        MarkdownMemoryStore memoryStore = new MarkdownMemoryStore(WorkspacePaths.LONG_TERM_MEMORY);
        MemoryPromptRenderer memoryPromptRenderer = new MemoryPromptRenderer(longTermMemorySystemPrompt);
        TodoStore todoStore = new JsonTodoStore(objectMapper, WorkspacePaths.TODO_FILE);
        ScheduledUserMessageStore scheduledUserMessageStore = new JsonScheduledUserMessageStore(objectMapper, WorkspacePaths.SCHEDULE_FILE);
        ScheduledUserMessageManager scheduledUserMessageManager = new ScheduledUserMessageManager(scheduledUserMessageStore, sessionName);
        RoomStore roomStore = new JsonRoomStore(objectMapper, WorkspacePaths.ROOM_INDEX);
        RoomHub roomHub = new RoomHub(new JsonlRoomMessageStore(objectMapper, WorkspacePaths.ROOM_MESSAGES));
        RoomAgentPromptStore roomAgentPromptStore = new RoomAgentPromptStore(WorkspacePaths.ROOM_AGENT_PROMPTS);
        RoomAgentSessionCleaner roomAgentSessionCleaner = new RoomAgentSessionCleaner(WorkspacePaths.ROOM_AGENT_SESSIONS);
        RoomMembershipStore roomMembershipStore = new JsonRoomMembershipStore(objectMapper, WorkspacePaths.ROOM_MEMBERS);
        RoomAgentRegistry roomAgentRegistry = new JsonRoomAgentRegistry(
                objectMapper,
                WorkspacePaths.ROOM_AGENT_INDEX,
                roomAgentPromptStore
        );
        new RoomAgentTemplateSeeder(objectMapper, roomAgentRegistry).seedIfEmpty();

        List<Message> bootstrapMessages = new ArrayList<>();
        // 基础 system prompt 来自 jar 内置 resources/prompts/agent/system.md。
        bootstrapMessages.add(Message.system(systemPrompt));

        JsonlSessionStore persistentSessionStore = JsonlSessionStore.openNamed(
                objectMapper,
                WorkspacePaths.SESSIONS,
                sessionName
        );
        TokenEstimator tokenEstimator = new SimpleTokenEstimator();
        Summarizer transcriptFallback = new TranscriptSummarizer(contextSummaryPrompt, 8_000);
        Summarizer contextSummarizer = new LlmSummarizer(
                provider.defaultModel(),
                streamingChatClient,
                contextSummaryPrompt,
                8_000,
                transcriptFallback
        );
        ContextOptions contextOptions = ContextOptions.defaults();
        ContextWindowSnapshotStore contextWindowSnapshotStore = new JsonContextWindowSnapshotStore(
                objectMapper,
                WorkspacePaths.CONTEXT_WINDOWS
        );
        ContextWindowSnapshotSessionStore.SnapshotMetadata snapshotMetadata = new ContextWindowSnapshotSessionStore.SnapshotMetadata(
                sessionName,
                SessionReplayer.MAIN_BRANCH,
                sha256(systemPrompt),
                sha256(contextSummaryPrompt),
                CONTEXT_SUMMARIZER_ID,
                provider.defaultModel()
        );
        ContextWindowRestoreResult contextWindowRestoreResult = restoreOrBuildContextWindowCache(
                bootstrapMessages,
                persistentSessionStore,
                contextWindowSnapshotStore,
                snapshotMetadata,
                tokenEstimator,
                contextSummarizer,
                contextOptions
        );
        ContextWindowCache contextWindowCache = contextWindowRestoreResult.cache();
        saveContextWindowSnapshot(
                contextWindowCache,
                contextWindowSnapshotStore,
                snapshotMetadata,
                contextWindowRestoreResult.lastSeq(),
                contextWindowRestoreResult.lastHash()
        );
        SessionStore sessionStore = new ContextWindowSnapshotSessionStore(
                bootstrapMessages,
                persistentSessionStore,
                contextWindowCache,
                contextWindowSnapshotStore,
                snapshotMetadata
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
                        new ReminderTaskHandler(),
                        new TodoScanTaskHandler(todoStore),
                        new MemoryExtractionTaskHandler(memoryExtractionAgent)
                )
        );
        ensureTodoScanTask(backgroundTaskManager);
        backgroundTaskManager.start();
        HookRegistry hookRegistry = new HookRegistry();
        ToolApprovalManager toolApprovalManager = new ToolApprovalManager();

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
                backgroundTaskManager,
                scheduledUserMessageManager,
                toolApprovalManager,
                todoStore
        );
        RuntimeExtensionRegistry extensionRegistry = RuntimeExtensionRegistry.defaults();
        extensionRegistry.registerTools(extensionContext);
        extensionRegistry.registerHooks(extensionContext);
        ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4);

        List<AgentEventHandler> eventHandlers = new ArrayList<>();
        eventHandlers.add(eventHandler);
        eventHandlers.addAll(extensionRegistry.eventHandlers(extensionContext));
        AgentEventBus eventBus = new AgentEventBus(sessionName, eventHandlers);
        eventPublisher.attach(eventBus);
        toolApprovalManager.attachEventBus(eventBus);
        ContextPipeline contextPipeline = new ContextPipeline(contextWindowCache);

        AgentLoop agentLoop = new AgentLoop(
                chatModel::get,
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
        scheduledUserMessageManager.start(schedule -> runCoordinator.submit(scheduledUserMessageManager.renderUserInput(schedule)));
        AgentTeamRunner agentTeamRunner = new AgentTeamRunner(
                eventPublisher,
                new TeamTaskExecutor(
                        new TeamAgentFactory(
                                Path.of("."),
                                objectMapper,
                                httpClient,
                                provider,
                                streamingChatClient,
                                teamPromptSet
                        ),
                        eventPublisher
                )
        );
        PlanModeCoordinator planModeCoordinator = new PlanModeCoordinator(
                new PlanPlannerAgent(
                        objectMapper,
                        provider,
                        streamingChatClient,
                        planPlannerSystemPrompt
                ),
                new PlanTaskExecutor(
                        provider,
                        streamingChatClient,
                        toolRegistry,
                        hookRegistry,
                        eventPublisher,
                        planTaskExecutorSystemPrompt
                ),
                runCoordinator,
                eventPublisher,
                planFinalSummaryUserPrompt
        );
        RoomPromptBuilder roomPromptBuilder = new RoomPromptBuilder(roomAgentWrapperSystemPrompt);
        RoomCoordinator roomCoordinator = new RoomCoordinator(
                roomStore,
                roomHub,
                roomAgentRegistry,
                roomMembershipStore,
                new RoomMentionParser(),
                new RoomAgentRunner(
                        provider,
                        streamingChatClient,
                        roomHub,
                        roomAgentPromptStore,
                        new RoomAgentSessionFactory(objectMapper, WorkspacePaths.ROOM_AGENT_SESSIONS),
                        new RoomToolRegistryFactory(Path.of("."), objectMapper, httpClient, skillRepository),
                        roomPromptBuilder
                )
        );

        return new AgentRuntime(
                agentLoop,
                runCoordinator,
                agentTeamRunner,
                planModeCoordinator,
                roomStore,
                roomHub,
                roomCoordinator,
                roomAgentRegistry,
                roomAgentPromptStore,
                roomMembershipStore,
                roomAgentSessionCleaner,
                eventPublisher,
                backgroundTaskManager,
                scheduledUserMessageManager,
                toolApprovalManager,
                parallelToolExecutor,
                mcpToolExecutor,
                provider,
                chatModel,
                availableChatModels,
                sessionName,
                teamFinalSummaryUserPrompt,
                skillRepository.listMetadata().size()
        );
    }

    /**
     * 返回当前主 Chat runtime 可切换的模型集合。
     *
     * <p>第一版只开放 DeepSeek V4 flash/pro。其他 OpenAI-compatible provider
     * 仍使用启动时配置的默认模型，避免把供应商能力判断扩散到 UI。</p>
     */
    private List<String> availableChatModels(OpenAiCompatibleProvider provider) {
        if ("deepseek".equalsIgnoreCase(provider.name())) {
            return DeepSeekModels.switchableChatModels();
        }
        return List.of(provider.defaultModel());
    }

    /**
     * 计算启动时的 chat 模型。
     *
     * <p>如果 .env 指定了允许切换的模型，就沿用；否则回退到当前列表第一个模型。</p>
     */
    private String initialChatModel(OpenAiCompatibleProvider provider, List<String> availableChatModels) {
        if (availableChatModels.contains(provider.defaultModel())) {
            return provider.defaultModel();
        }
        return availableChatModels.getFirst();
    }

    /**
     * 从 snapshot 恢复上下文窗口；无效时回退到 JSONL 全量重建。
     */
    private ContextWindowRestoreResult restoreOrBuildContextWindowCache(
            List<Message> bootstrapMessages,
            JsonlSessionStore sessionStore,
            ContextWindowSnapshotStore snapshotStore,
            ContextWindowSnapshotSessionStore.SnapshotMetadata metadata,
            TokenEstimator tokenEstimator,
            Summarizer summarizer,
            ContextOptions contextOptions
    ) throws IOException {
        ContextWindowCache cache = new ContextWindowCache(tokenEstimator, summarizer, contextOptions);
        Optional<ContextWindowSnapshot> snapshot = loadSnapshotQuietly(snapshotStore, metadata);
        if (snapshot.isPresent() && isSnapshotMetadataValid(snapshot.get(), metadata)) {
            try {
                ContextWindowSnapshot currentSnapshot = snapshot.get();
                if (snapshotSeqMatches(sessionStore, currentSnapshot)) {
                    List<SessionMessageRecord> recordsAfterSnapshot = sessionStore.loadMessageRecordsAfter(currentSnapshot.lastSeq());
                    cache.restore(bootstrapMessages, currentSnapshot);
                    appendRecordsAfter(cache, recordsAfterSnapshot, currentSnapshot.lastSeq());
                    cache.build();
                    SessionMessageRecord lastRecord = recordsAfterSnapshot.isEmpty() ? null : recordsAfterSnapshot.getLast();
                    return new ContextWindowRestoreResult(
                            cache,
                            lastRecord == null ? currentSnapshot.lastSeq() : lastRecord.seq(),
                            lastRecord == null ? currentSnapshot.lastHash() : lastRecord.hash()
                    );
                }
            } catch (IOException | RuntimeException ignored) {
                // 快照内容损坏或协议不合法时，直接回退到 JSONL 全量重建。
            }
        }

        SessionReplayResult replayResult = sessionStore.replay();
        cache.initialize(bootstrapMessages, replayResult.messageRecords());
        SessionMessageRecord lastRecord = replayResult.messageRecords().isEmpty()
                ? null
                : replayResult.messageRecords().getLast();
        return new ContextWindowRestoreResult(
                cache,
                lastRecord == null ? 0 : lastRecord.seq(),
                lastRecord == null ? "" : lastRecord.hash()
        );
    }

    private Optional<ContextWindowSnapshot> loadSnapshotQuietly(
            ContextWindowSnapshotStore snapshotStore,
            ContextWindowSnapshotSessionStore.SnapshotMetadata metadata
    ) {
        try {
            return snapshotStore.load(metadata.sessionId(), metadata.branchId());
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 校验快照是否还能对应当前 prompt 配置。
     *
     * <p>chat 模型可以在运行中切换，因此模型名不参与快照失效判断；
     * 真正会改变压缩语义的是 system prompt、summary prompt 和 summarizer。</p>
     */
    private boolean isSnapshotMetadataValid(
            ContextWindowSnapshot snapshot,
            ContextWindowSnapshotSessionStore.SnapshotMetadata metadata
    ) {
        if (snapshot.version() != ContextWindowSnapshot.CURRENT_VERSION) {
            return false;
        }
        if (!Objects.equals(snapshot.sessionId(), metadata.sessionId())
                || !Objects.equals(snapshot.branchId(), metadata.branchId())
                || !Objects.equals(snapshot.systemPromptHash(), metadata.systemPromptHash())
                || !Objects.equals(snapshot.summaryPromptHash(), metadata.summaryPromptHash())
                || !Objects.equals(snapshot.summarizer(), metadata.summarizer())) {
            return false;
        }
        if (snapshot.lastSeq() == 0) {
            return snapshot.lastHash() == null || snapshot.lastHash().isBlank();
        }
        return snapshot.lastHash() != null && !snapshot.lastHash().isBlank();
    }

    private boolean snapshotSeqMatches(JsonlSessionStore sessionStore, ContextWindowSnapshot snapshot) throws IOException {
        if (snapshot.lastSeq() == 0) {
            return true;
        }
        return sessionStore.containsEvent(snapshot.lastSeq(), snapshot.lastHash());
    }

    private void appendRecordsAfter(ContextWindowCache cache, List<SessionMessageRecord> records, long lastSeq) {
        records.stream()
                .filter(record -> record.seq() > lastSeq)
                .forEach(record -> cache.append(record.message()));
    }

    /**
     * 保存当前上下文窗口快照。
     */
    private void saveContextWindowSnapshot(
            ContextWindowCache cache,
            ContextWindowSnapshotStore snapshotStore,
            ContextWindowSnapshotSessionStore.SnapshotMetadata metadata,
            long lastSeq,
            String lastHash
    ) throws IOException {
        snapshotStore.save(cache.snapshot(
                metadata.sessionId(),
                metadata.branchId(),
                lastSeq,
                lastHash == null ? "" : lastHash,
                metadata.systemPromptHash(),
                metadata.summaryPromptHash(),
                metadata.summarizer(),
                metadata.model()
        ));
    }

    private String sha256(String value) throws IOException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 not available", error);
        }
    }

    private record ContextWindowRestoreResult(ContextWindowCache cache, long lastSeq, String lastHash) {
    }

    /**
     * 创建后台任务框架。
     *
     * <p>后台框架只负责扫描调度、执行记录和事件通知。
     * 具体动作由 handlers 决定，例如 reminder 或长期记忆抽取。</p>
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
                new BackgroundTaskScheduler(store, executor)
        );
    }

    private void ensureTodoScanTask(BackgroundTaskManager backgroundTaskManager) throws IOException {
        boolean exists = backgroundTaskManager.listTasks().stream()
                .anyMatch(task -> task.enabled()
                        && task.status() == TaskStatus.ACTIVE
                        && task.action() != null
                        && TodoScanTaskHandler.ACTION_TYPE.equals(task.action().type()));
        if (exists) {
            return;
        }
        backgroundTaskManager.create(BackgroundTask.create(
                "便签待办扫描",
                TaskTrigger.interval(10),
                new TaskAction(TodoScanTaskHandler.ACTION_TYPE, Map.of())
        ));
    }

}
