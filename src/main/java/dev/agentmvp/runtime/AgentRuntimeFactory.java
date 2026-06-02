package dev.agentmvp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.agent.AgentEventHandler;
import dev.agentmvp.agent.AgentLoop;
import dev.agentmvp.context.ContextBuilder;
import dev.agentmvp.context.SimpleTokenEstimator;
import dev.agentmvp.context.TranscriptSummarizer;
import dev.agentmvp.context.model.ContextOptions;
import dev.agentmvp.llm.OpenAiCompatibleChatClient;
import dev.agentmvp.llm.OpenAiCompatibleProviderFactory;
import dev.agentmvp.llm.StreamingChatClient;
import dev.agentmvp.llm.model.Message;
import dev.agentmvp.llm.model.OpenAiCompatibleProvider;
import dev.agentmvp.mcp.McpToolLoader;
import dev.agentmvp.mcp.config.McpClientFactory;
import dev.agentmvp.mcp.config.McpConfigLoader;
import dev.agentmvp.mcp.config.model.McpConfig;
import dev.agentmvp.mcp.config.model.McpServerConfig;
import dev.agentmvp.mcp.McpToolExecutor;
import dev.agentmvp.prompt.PromptLoader;
import dev.agentmvp.prompt.PromptPaths;
import dev.agentmvp.session.InMemorySessionStore;
import dev.agentmvp.skill.SkillIndexRenderer;
import dev.agentmvp.skill.SkillRepository;
import dev.agentmvp.tool.LocalToolExecutor;
import dev.agentmvp.tool.ParallelToolExecutor;
import dev.agentmvp.tool.ToolRegistry;
import dev.agentmvp.tool.builtin.BuiltinTools;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * 创建 Agent 运行时。
 *
 * <p>入口层只负责 UI。Prompt、Skill、Tool、LLM、ContextBuilder 的装配集中在这里，
 * 避免 TUI 里混入一大段和界面无关的初始化代码。</p>
 */
public class AgentRuntimeFactory {
    /**
     * 使用默认配置创建 Agent 运行时。
     */
    public AgentRuntime create(AgentEventHandler eventHandler) throws IOException {
        Objects.requireNonNull(eventHandler);

        OpenAiCompatibleProvider provider = OpenAiCompatibleProviderFactory.fromEnvWithDeepSeekDefaults();
        if (provider.apiKey() == null || provider.apiKey().isBlank()) {
            throw new IllegalStateException("Missing API key. Set DEEPSEEK_API_KEY or OPENAI_COMPATIBLE_API_KEY.");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        PromptLoader promptLoader = new PromptLoader();
        String systemPrompt = promptLoader.load(PromptPaths.SYSTEM);
        String contextSummaryPrompt = promptLoader.load(PromptPaths.CONTEXT_SUMMARY);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();

        StreamingChatClient streamingChatClient = OpenAiCompatibleChatClient.create(httpClient, objectMapper, provider);
        LocalToolExecutor localToolExecutor = new LocalToolExecutor(objectMapper);
        McpToolExecutor mcpToolExecutor = new McpToolExecutor();
        ToolRegistry toolRegistry = new ToolRegistry(localToolExecutor, mcpToolExecutor);

        SkillRepository skillRepository = SkillRepository.scan(Path.of("skills"));
        // 所有本地内置工具最终都通过 ToolRegistry.registerLocal 注册。
        BuiltinTools.registerAll(toolRegistry, Path.of("."), skillRepository);
        loadConfiguredMcpServers(objectMapper, httpClient, toolRegistry, mcpToolExecutor);
        ParallelToolExecutor parallelToolExecutor = ParallelToolExecutor.fixedPool(toolRegistry, 4);

        InMemorySessionStore sessionStore = new InMemorySessionStore();
        // 基础 system prompt 来自 jar 内置 resources/prompts/system.md。
        sessionStore.append(Message.system(systemPrompt));

        String skillIndex = new SkillIndexRenderer().render(skillRepository.listMetadata());
        if (!skillIndex.isBlank()) {
            // Skill 索引只包含 name/description；完整 SKILL.md 由 load_skill 按需加载。
            sessionStore.append(Message.system(skillIndex));
        }

        AgentLoop agentLoop = new AgentLoop(
                provider,
                sessionStore,
                new ContextBuilder(
                        new SimpleTokenEstimator(),
                        new TranscriptSummarizer(contextSummaryPrompt, 8_000),
                        ContextOptions.defaults()
                ),
                streamingChatClient,
                toolRegistry,
                parallelToolExecutor,
                eventHandler,
                8
        );

        return new AgentRuntime(agentLoop, parallelToolExecutor, mcpToolExecutor, provider, skillRepository.listMetadata().size());
    }

    /**
     * 从 mcp.json 加载外部 MCP Server。
     *
     * <p>没有 mcp.json 时什么都不做；有 mcp.json 时，每个 server 都会经历：
     * 创建客户端 -> initialize -> tools/list -> 注册成普通 Tool。</p>
     */
    private void loadConfiguredMcpServers(
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            ToolRegistry toolRegistry,
            McpToolExecutor mcpToolExecutor
    ) throws IOException {
        McpConfig mcpConfig = new McpConfigLoader(objectMapper).loadIfExists(Path.of("mcp.json"));
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
