package com.aster.app.tool.developer;

import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.tool.builtin.BuiltinTools;
import com.aster.core.agent.AgentLoop;
import com.aster.core.context.ContextBuilder;
import com.aster.core.context.SimpleTokenEstimator;
import com.aster.core.context.TranscriptSummarizer;
import com.aster.core.context.model.ContextOptions;
import com.aster.core.event.AgentEventBus;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.BootstrappedSessionStore;
import com.aster.core.session.InMemorySessionStore;
import com.aster.core.session.SessionStore;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ParallelToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.aster.core.tool.model.Tool;
import com.aster.core.tool.model.ToolResult;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.Message;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.aster.llm.text.model.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * subagent 扩展工具。
 *
 * <p>它为单次子任务创建一个内存 session 的子 Agent。
 * 子 Agent 可以使用基础工具和开发者检索/Web 工具，但不会再次拥有 subagent 工具。</p>
 */
public class SubagentTool extends AbstractDeveloperTool {
    private static final int MAX_TOOL_ROUNDS = 100;
    private static final int MAX_OUTPUT_CHARS = 6_000;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final OpenAiCompatibleProvider provider;
    private final StreamingChatClient streamingChatClient;

    public SubagentTool(
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient
    ) {
        super(workingDirectory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.provider = Objects.requireNonNull(provider);
        this.streamingChatClient = Objects.requireNonNull(streamingChatClient);
    }

    @Override
    public Tool definition() {
        return Tool.local(
                "subagent",
                "SubAgent",
                """
                        启动一个无状态子 Agent 执行独立任务。适合开放式搜索、代码定位或需要多步工具调用的研究任务。
                        子 Agent 的结果只返回给当前 Agent，用户不会直接看到；最终仍需要由当前 Agent 总结给用户。
                        """.strip(),
                objectSchema(
                        Map.of("task", stringSchema("子 Agent 要执行的完整任务说明")),
                        List.of("task")
                )
        );
    }

    /**
     * 创建并运行一次子 Agent。
     */
    @Override
    public ToolResult execute(ToolCall call, Map<String, Object> arguments) throws Exception {
        String task = requiredString(arguments, "task");
        ToolRegistry subToolRegistry = new ToolRegistry(new LocalToolExecutor(objectMapper), new McpToolExecutor());
        BuiltinTools.registerAll(subToolRegistry, workingDirectory);
        DeveloperTools.registerAll(
                subToolRegistry,
                workingDirectory,
                objectMapper,
                httpClient,
                provider,
                streamingChatClient,
                false
        );

        SessionStore sessionStore = new BootstrappedSessionStore(
                List.of(Message.system(systemPrompt())),
                new InMemorySessionStore()
        );
        try (ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(subToolRegistry, 4)) {
            AgentLoop subAgent = new AgentLoop(
                    provider,
                    sessionStore,
                    new ContextBuilder(
                            new SimpleTokenEstimator(),
                            new TranscriptSummarizer(4_000),
                            ContextOptions.defaults()
                    ),
                    streamingChatClient,
                    subToolRegistry,
                    parallelToolExecutor,
                    HookRegistry.empty(),
                    AgentEventBus.noop("subagent"),
                    MAX_TOOL_ROUNDS
            );
            String answer = subAgent.run(task);
            return ToolResult.text(call.id(), "[子 Agent 已完成]\n" + truncate(answer, MAX_OUTPUT_CHARS));
        }
    }

    private String systemPrompt() {
        return """
                你是 Aster 的子 Agent，负责完成父 Agent 委派的独立任务。

                规则：
                - 你可以使用工具搜索、读取和必要时修改文件。
                - 如果任务只要求研究或定位代码，不要修改文件。
                - 最终回答必须简洁，列出关键发现、相关文件路径和必要证据。
                - 你的输出只会返回给父 Agent，不会直接展示给用户。
                """.strip();
    }
}
