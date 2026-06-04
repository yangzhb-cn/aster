package com.aster;

import com.aster.app.background.BackgroundTaskEventBus;
import com.aster.app.background.BackgroundTaskExecutor;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.BackgroundTaskScheduler;
import com.aster.app.background.JsonlBackgroundTaskStore;
import com.aster.app.background.ReminderTaskHandler;
import com.aster.app.extension.DeveloperToolExtension;
import com.aster.app.extension.RuntimeExtensionContext;
import com.aster.app.hitl.ToolApprovalManager;
import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.app.schedule.JsonScheduledUserMessageStore;
import com.aster.app.schedule.ScheduledUserMessageManager;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.skill.SkillRepository;
import com.aster.app.todo.JsonTodoStore;
import com.aster.app.tool.builtin.BuiltinTools;
import com.aster.app.tool.developer.WebSearchTool;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.InMemorySessionStore;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.aster.llm.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
 * 开发者扩展工具测试。
 *
 * <p>这批工具通过 RuntimeExtension 注册，不进入 BuiltinTools 四件套。</p>
 */
class DeveloperToolsTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证开发者工具按扩展方式注册，且 BuiltinTools 仍然只包含四个基础工具。
     */
    @Test
    void developerToolsRegisterThroughRuntimeExtension() throws Exception {
        ToolRegistry registry = createRegistry(tempDir);
        BuiltinTools.registerAll(registry, tempDir);

        assertEquals(List.of("read", "write", "bash", "edit"), toolNames(registry));

        new DeveloperToolExtension().registerTools(extensionContext(registry, new McpToolExecutor(), emptySkillRepository()));

        assertEquals(List.of(
                "read",
                "write",
                "bash",
                "edit",
                "ls",
                "glob",
                "grep",
                "web_fetch",
                "web_search",
                "subagent"
        ), toolNames(registry));
    }

    /**
     * 验证 ls/glob/grep 能完成基础文件检索。
     */
    @Test
    void fileSearchToolsWork() throws Exception {
        Path source = tempDir.resolve("src");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Alpha.java"), "class Alpha {\n  void run() {}\n}\n");
        Files.writeString(source.resolve("Beta.md"), "Alpha note\n");

        ToolRegistry registry = registryWithDeveloperTools();

        ToolResult ls = execute(registry, "ls", Map.of("path", source.toString()));
        ToolResult glob = execute(registry, "glob", Map.of("pattern", "**/*.java", "path", tempDir.toString()));
        ToolResult grep = execute(registry, "grep", Map.of(
                "pattern", "void\\s+run",
                "path", tempDir.toString(),
                "include", "*.java"
        ));

        assertFalse(ls.error());
        assertTrue(ls.renderText().contains("Alpha.java"));
        assertFalse(glob.error());
        assertTrue(glob.renderText().contains("Alpha.java"));
        assertFalse(grep.error());
        assertTrue(grep.renderText().contains("Alpha.java:2"));
    }

    /**
     * 验证 web_fetch 会把 HTML 转成简化文本。
     */
    @Test
    void webFetchConvertsHtmlToText() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html><head><style>.x{}</style></head><body><h1>Aster</h1><p>Hello &amp; world</p></body></html>"));
            server.start();

            ToolRegistry registry = registryWithDeveloperTools();
            ToolResult result = execute(registry, "web_fetch", Map.of("url", server.url("/doc").toString()));

            assertFalse(result.error());
            assertTrue(result.renderText().contains("URL:"));
            assertTrue(result.renderText().contains("Aster"));
            assertTrue(result.renderText().contains("Hello & world"));
        }
    }

    /**
     * 验证 web_search 缺少 Tavily key 时返回清晰错误，不访问真实网络。
     */
    @Test
    void webSearchReportsMissingApiKey() throws Exception {
        WebSearchTool tool = new WebSearchTool(
                tempDir,
                objectMapper,
                new OkHttpClient(),
                Map.of()
        );

        ToolResult result = tool.execute(ToolCall.function(
                "call_web_search",
                "web_search",
                objectMapper.writeValueAsString(Map.of("query", "Aster"))
        ), Map.of("query", "Aster"));

        assertTrue(result.error());
        assertTrue(result.renderText().contains("TAVILY_API_KEY"));
    }

    private ToolRegistry registryWithDeveloperTools() throws Exception {
        ToolRegistry registry = createRegistry(tempDir);
        new DeveloperToolExtension().registerTools(extensionContext(registry, new McpToolExecutor(), emptySkillRepository()));
        return registry;
    }

    private ToolRegistry createRegistry(Path workingDirectory) {
        return new ToolRegistry(new LocalToolExecutor(objectMapper), new McpToolExecutor());
    }

    private SkillRepository emptySkillRepository() throws Exception {
        return SkillRepository.scan(tempDir.resolve("skills"));
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

    private ToolResult execute(ToolRegistry registry, String name, Map<String, Object> arguments) throws Exception {
        return registry.execute(ToolCall.function(
                "call_" + name,
                name,
                objectMapper.writeValueAsString(arguments)
        ));
    }

    private List<String> toolNames(ToolRegistry registry) {
        return registry.listTools().stream()
                .map(tool -> tool.name())
                .toList();
    }
}
