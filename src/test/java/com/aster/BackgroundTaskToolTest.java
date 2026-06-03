package com.aster;

import com.aster.app.background.BackgroundTaskEventBus;
import com.aster.app.background.BackgroundTaskExecutor;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.BackgroundTaskScheduler;
import com.aster.app.background.JsonlBackgroundTaskStore;
import com.aster.app.background.ReminderTaskHandler;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskRun;
import com.aster.app.background.model.TaskRunStatus;
import com.aster.app.background.model.TaskStatus;
import com.aster.app.extension.BackgroundTaskToolExtension;
import com.aster.app.extension.RuntimeExtensionContext;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.skill.SkillRepository;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.InMemorySessionStore;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * background_task 扩展工具测试。
 *
 * <p>工具只通过 RuntimeExtension 注册，并通过 BackgroundTaskManager 操作任务。</p>
 */
class BackgroundTaskToolTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证 background_task 通过扩展注册到工具表。
     */
    @Test
    void registersBackgroundTaskToolThroughExtension() throws Exception {
        TestRuntime runtime = createRuntime();

        try {
            assertEquals(List.of("background_task"), runtime.registry().listTools().stream()
                    .map(tool -> tool.name())
                    .toList());
        } finally {
            runtime.manager().close();
        }
    }

    /**
     * 验证 Agent 可以创建、查看和取消固定间隔后台任务。
     */
    @Test
    void createsListsAndCancelsIntervalTask() throws Exception {
        TestRuntime runtime = createRuntime();

        try {
            ToolResult create = execute(runtime.registry(), Map.of(
                    "action", "create_interval",
                    "name", "测试 interval",
                    "taskType", "reminder",
                    "params", Map.of("text", "interval reminder"),
                    "intervalSeconds", 60
            ));
            String taskId = runtime.store().listTasks().get(0).id();
            ToolResult list = execute(runtime.registry(), Map.of("action", "list"));
            ToolResult cancel = execute(runtime.registry(), Map.of(
                    "action", "cancel",
                    "taskId", taskId
            ));

            BackgroundTask latest = runtime.store().findTask(taskId).orElseThrow();
            assertFalse(create.error());
            assertTrue(create.renderText().contains("后台任务已创建"));
            assertTrue(list.renderText().contains("测试 interval"));
            assertFalse(cancel.error());
            assertEquals(TaskStatus.CANCELLED, latest.status());
        } finally {
            runtime.manager().close();
        }
    }

    /**
     * 验证 immediate 任务会进入现有后台执行链路并写入运行记录。
     */
    @Test
    void createsImmediateTaskAndRecordsRun() throws Exception {
        TestRuntime runtime = createRuntime();

        try {
            ToolResult create = execute(runtime.registry(), Map.of(
                    "action", "create_immediate",
                    "name", "测试 immediate",
                    "taskType", "reminder",
                    "params", Map.of("text", "立即提醒")
            ));
            List<TaskRun> runs = waitForRuns(runtime.store(), 1);

            assertFalse(create.error());
            assertEquals(1, runs.size());
            assertEquals(TaskRunStatus.SUCCESS, runs.get(0).status());
            assertEquals("立即提醒", runs.get(0).message());
        } finally {
            runtime.manager().close();
        }
    }

    /**
     * 验证 reminder 动作可以通过 background_task 创建并产出提醒正文。
     */
    @Test
    void createsReminderTaskAndRecordsReminderText() throws Exception {
        TestRuntime runtime = createRuntime();

        try {
            ToolResult create = execute(runtime.registry(), Map.of(
                    "action", "create_immediate",
                    "name", "测试提醒",
                    "taskType", "reminder",
                    "params", Map.of("text", "该看结果了")
            ));
            List<TaskRun> runs = waitForRuns(runtime.store(), 1);

            assertFalse(create.error());
            assertEquals(1, runs.size());
            assertEquals(TaskRunStatus.SUCCESS, runs.get(0).status());
            assertEquals("该看结果了", runs.get(0).message());
        } finally {
            runtime.manager().close();
        }
    }

    private TestRuntime createRuntime() throws Exception {
        JsonlBackgroundTaskStore store = new JsonlBackgroundTaskStore(
                objectMapper,
                tempDir.resolve("tasks.jsonl"),
                tempDir.resolve("runs.jsonl")
        );
        BackgroundTaskExecutor executor = new BackgroundTaskExecutor(
                store,
                List.of(new ReminderTaskHandler()),
                BackgroundTaskEventBus.noop()
        );
        BackgroundTaskManager manager = new BackgroundTaskManager(
                store,
                new BackgroundTaskScheduler(store, executor)
        );
        ToolRegistry registry = new ToolRegistry(new LocalToolExecutor(objectMapper), new McpToolExecutor());
        new BackgroundTaskToolExtension().registerTools(extensionContext(registry, manager));
        return new TestRuntime(registry, manager, store);
    }

    private RuntimeExtensionContext extensionContext(ToolRegistry registry, BackgroundTaskManager manager) throws Exception {
        McpToolExecutor mcpToolExecutor = new McpToolExecutor();
        return new RuntimeExtensionContext(
                objectMapper,
                new OkHttpClient(),
                new OpenAiCompatibleProvider("fake", "http://localhost", "test-key", "fake-model"),
                (request, handler) -> {
                },
                new InMemorySessionStore(),
                registry,
                HookRegistry.empty(),
                mcpToolExecutor,
                SkillRepository.scan(tempDir.resolve("skills")),
                new MarkdownMemoryStore(tempDir.resolve("memory.md")),
                new MemoryPromptRenderer("{{memory}}"),
                manager,
                new ToolApprovalManager()
        );
    }

    private ToolResult execute(ToolRegistry registry, Map<String, Object> arguments) throws Exception {
        return registry.execute(ToolCall.function(
                "call_background_task",
                "background_task",
                objectMapper.writeValueAsString(arguments)
        ));
    }

    private List<TaskRun> waitForRuns(JsonlBackgroundTaskStore store, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            List<TaskRun> runs = store.listRuns();
            if (runs.size() >= expected) {
                return runs;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return store.listRuns();
    }

    private record TestRuntime(
            ToolRegistry registry,
            BackgroundTaskManager manager,
            JsonlBackgroundTaskStore store
    ) {
    }
}
