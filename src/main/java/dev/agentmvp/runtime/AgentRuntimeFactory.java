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
        ToolRegistry toolRegistry = new ToolRegistry(localToolExecutor, new McpToolExecutor());

        SkillRepository skillRepository = SkillRepository.scan(Path.of("skills"));
        // 所有本地内置工具最终都通过 ToolRegistry.registerLocal 注册。
        BuiltinTools.registerAll(toolRegistry, Path.of("."), skillRepository);
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

        return new AgentRuntime(agentLoop, parallelToolExecutor, provider, skillRepository.listMetadata().size());
    }
}
