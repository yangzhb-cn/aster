package com.aster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.app.background.BackgroundTaskEventBus;
import com.aster.app.background.BackgroundTaskExecutor;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.BackgroundTaskScheduler;
import com.aster.app.background.JsonlBackgroundTaskStore;
import com.aster.app.background.ReminderTaskHandler;
import com.aster.app.extension.RuntimeExtensionContext;
import com.aster.app.extension.SkillToolExtension;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.app.schedule.JsonScheduledUserMessageStore;
import com.aster.app.schedule.ScheduledUserMessageManager;
import com.aster.llm.text.model.ToolCall;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.skill.SkillRepository;
import com.aster.app.todo.JsonTodoStore;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.InMemorySessionStore;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.app.tool.builtin.BuiltinTools;
import com.aster.core.tool.model.ToolResult;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四个基础内置工具测试。
 *
 * <p>这些测试不经过 LLM，直接用 ToolRegistry 调工具，验证工具自身行为。</p>
 */
class BuiltinToolsTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证四个工具会注册到 ToolRegistry。
     */
    @Test
    void registersFourBuiltinTools() {
        ToolRegistry registry = createRegistry(tempDir);

        assertEquals(List.of("read", "write", "bash", "edit"), registry.listTools().stream()
                .map(tool -> tool.name())
                .toList());
    }

    /**
     * 验证存在 Skill 时，Skill 扩展会额外注册 load_skill。
     */
    @Test
    void skillExtensionRegistersLoadSkillWhenSkillsExist() throws Exception {
        Path skillsDirectory = tempDir.resolve("skills");
        Path skillDirectory = skillsDirectory.resolve("web-access");
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: web-access
                description: 网页访问经验
                ---

                # web-access

                这里是完整 Skill 正文。
                """);

        SkillRepository skillRepository = SkillRepository.scan(skillsDirectory);
        LocalToolExecutor localToolExecutor = new LocalToolExecutor(objectMapper);
        McpToolExecutor mcpToolExecutor = new McpToolExecutor();
        ToolRegistry registry = new ToolRegistry(localToolExecutor, mcpToolExecutor);
        BuiltinTools.registerAll(registry, tempDir);
        new SkillToolExtension().registerTools(extensionContext(registry, mcpToolExecutor, skillRepository));

        ToolResult result = execute(registry, "load_skill", Map.of("name", "web-access"));

        assertEquals(List.of("read", "write", "bash", "edit", "load_skill"), registry.listTools().stream()
                .map(tool -> tool.name())
                .toList());
        assertFalse(result.error());
        assertTrue(result.renderText().contains("这里是完整 Skill 正文"));
    }

    /**
     * 验证 write/read 可以使用工作目录之外的绝对路径。
     */
    @Test
    void writeAndReadDoNotRestrictAbsolutePaths() throws Exception {
        ToolRegistry registry = createRegistry(tempDir.resolve("work"));
        Path outside = tempDir.resolve("outside.txt");

        ToolResult write = execute(registry, "write", Map.of(
                "path", outside.toString(),
                "content", "abcdef"
        ));
        ToolResult read = execute(registry, "read", Map.of(
                "path", outside.toString(),
                "page", 2,
                "pageSizeBytes", 2
        ));

        assertFalse(write.error());
        assertEquals("abcdef", Files.readString(outside));
        assertTrue(read.renderText().startsWith("cd"));
    }

    /**
     * 验证 edit 可以一次修改多个不重叠区域。
     */
    @Test
    void editSupportsMultipleNonOverlappingReplacements() throws Exception {
        ToolRegistry registry = createRegistry(tempDir);
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "alpha beta gamma");

        ToolResult result = execute(registry, "edit", Map.of(
                "path", file.toString(),
                "replacements", List.of(
                        Map.of("oldText", "alpha", "newText", "one"),
                        Map.of("oldText", "gamma", "newText", "three")
                )
        ));

        assertFalse(result.error());
        assertEquals("one beta three", Files.readString(file));
        assertTrue(result.renderText().contains("替换次数: 2"));
    }

    /**
     * 验证 bash 输出只保留尾部内容。
     */
    @Test
    void bashKeepsTailOutput() throws Exception {
        ToolRegistry registry = createRegistry(tempDir);

        ToolResult result = execute(registry, "bash", Map.of(
                "command", "for i in $(seq 1 2105); do echo line-$i; done",
                "timeoutSeconds", 10
        ));

        String text = result.renderText();
        assertFalse(result.error());
        assertTrue(text.contains("line-2105"));
        assertFalse(text.contains("line-1\n"));
        assertTrue(text.contains("输出已截断"));
    }

    private ToolRegistry createRegistry(Path workingDirectory) {
        LocalToolExecutor localToolExecutor = new LocalToolExecutor(objectMapper);
        ToolRegistry registry = new ToolRegistry(localToolExecutor, new McpToolExecutor());
        BuiltinTools.registerAll(registry, workingDirectory);
        return registry;
    }

    private ToolResult execute(ToolRegistry registry, String name, Map<String, Object> arguments) throws Exception {
        return registry.execute(ToolCall.function(
                "call_" + name,
                name,
                objectMapper.writeValueAsString(arguments)
        ));
    }

    private RuntimeExtensionContext extensionContext(
            ToolRegistry registry,
            McpToolExecutor mcpToolExecutor,
            SkillRepository skillRepository
    ) throws Exception {
        JsonlBackgroundTaskStore store = new JsonlBackgroundTaskStore(
                objectMapper,
                tempDir.resolve("tasks.jsonl"),
                tempDir.resolve("runs.jsonl")
        );
        BackgroundTaskExecutor executor = new BackgroundTaskExecutor(
                store,
                List.of(new ReminderTaskHandler()),
                BackgroundTaskEventBus.single(ignored -> {
                })
        );
        BackgroundTaskManager backgroundTaskManager = new BackgroundTaskManager(
                store,
                new BackgroundTaskScheduler(store, executor)
        );
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
                skillRepository,
                new MarkdownMemoryStore(tempDir.resolve("memory.md")),
                new MemoryPromptRenderer("{{memory}}"),
                backgroundTaskManager,
                new ScheduledUserMessageManager(new JsonScheduledUserMessageStore(objectMapper, tempDir.resolve("schedules.json")), "test"),
                new ToolApprovalManager(),
                new JsonTodoStore(objectMapper, tempDir.resolve("todos.json"))
        );
    }
}
