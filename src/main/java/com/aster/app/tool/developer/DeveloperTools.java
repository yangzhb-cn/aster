package com.aster.app.tool.developer;

import com.aster.core.tool.ToolRegistry;
import com.aster.llm.text.StreamingChatClient;
import com.aster.llm.text.model.OpenAiCompatibleProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发者扩展工具注册入口。
 *
 * <p>这批工具只由 RuntimeExtension 调用注册，不进入 BuiltinTools 的四个基础工具集合。</p>
 */
public final class DeveloperTools {
    private DeveloperTools() {
    }

    /**
     * 注册开发者扩展工具。
     *
     * <p>includeSubagent=false 用于子 Agent 内部，避免 subagent 递归调用自身。</p>
     */
    public static void registerAll(
            ToolRegistry toolRegistry,
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient,
            boolean includeSubagent
    ) {
        for (DeveloperTool tool : defaultTools(
                workingDirectory,
                objectMapper,
                httpClient,
                provider,
                streamingChatClient,
                includeSubagent
        )) {
            tool.registerTo(toolRegistry);
        }
    }

    /**
     * 创建默认开发者扩展工具列表。
     */
    public static List<DeveloperTool> defaultTools(
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            OpenAiCompatibleProvider provider,
            StreamingChatClient streamingChatClient,
            boolean includeSubagent
    ) {
        List<DeveloperTool> tools = new ArrayList<>(List.of(
                new LsTool(workingDirectory),
                new GlobTool(workingDirectory),
                new GrepTool(workingDirectory),
                new WebFetchTool(workingDirectory, httpClient),
                new WebSearchTool(workingDirectory, objectMapper, httpClient)
        ));
        if (includeSubagent) {
            tools.add(new SubagentTool(
                    workingDirectory,
                    objectMapper,
                    httpClient,
                    provider,
                    streamingChatClient
            ));
        }
        return List.copyOf(tools);
    }
}
