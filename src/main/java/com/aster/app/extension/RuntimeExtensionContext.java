package com.aster.app.extension;

import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.memory.MarkdownMemoryStore;
import com.aster.app.memory.MemoryPromptRenderer;
import com.aster.app.skill.SkillRepository;
import com.aster.core.hook.HookRegistry;
import com.aster.core.session.SessionStore;
import com.aster.core.tool.ToolRegistry;
import com.aster.llm.StreamingChatClient;
import com.aster.llm.model.OpenAiCompatibleProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.util.Objects;

/**
 * 运行时扩展注册上下文。
 *
 * <p>这里集中暴露扩展注册所需的稳定对象。扩展通过这些对象注册能力，
 * 不直接创建 AgentLoop，也不直接修改 UI。</p>
 */
public record RuntimeExtensionContext(
        ObjectMapper objectMapper,
        OkHttpClient httpClient,
        OpenAiCompatibleProvider provider,
        StreamingChatClient streamingChatClient,
        SessionStore sessionStore,
        ToolRegistry toolRegistry,
        HookRegistry hookRegistry,
        McpToolExecutor mcpToolExecutor,
        SkillRepository skillRepository,
        MarkdownMemoryStore memoryStore,
        MemoryPromptRenderer memoryPromptRenderer,
        BackgroundTaskManager backgroundTaskManager
) {
    public RuntimeExtensionContext {
        Objects.requireNonNull(objectMapper);
        Objects.requireNonNull(httpClient);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(streamingChatClient);
        Objects.requireNonNull(sessionStore);
        Objects.requireNonNull(toolRegistry);
        Objects.requireNonNull(hookRegistry);
        Objects.requireNonNull(mcpToolExecutor);
        Objects.requireNonNull(skillRepository);
        Objects.requireNonNull(memoryStore);
        Objects.requireNonNull(memoryPromptRenderer);
        Objects.requireNonNull(backgroundTaskManager);
    }
}
