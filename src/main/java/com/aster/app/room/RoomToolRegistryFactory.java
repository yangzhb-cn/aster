package com.aster.app.room;

import com.aster.app.mcp.McpToolExecutor;
import com.aster.app.tool.builtin.LoadSkillTool;
import com.aster.app.tool.builtin.ReadTool;
import com.aster.app.tool.developer.GlobTool;
import com.aster.app.tool.developer.GrepTool;
import com.aster.app.tool.developer.LsTool;
import com.aster.app.tool.developer.WebFetchTool;
import com.aster.app.tool.developer.WebSearchTool;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.skill.SkillRepository;
import com.aster.core.tool.LocalToolExecutor;
import com.aster.core.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 创建房间 Agent 使用的受限工具注册表。
 *
 * <p>第一版允许只读和检索类工具，显式屏蔽 write、edit、bash、todo、background_task、
 * subagent 等会修改环境或递归启动任务的工具。</p>
 */
public class RoomToolRegistryFactory {
    private static final Set<String> FORBIDDEN = Set.of(
            "write",
            "edit",
            "bash",
            "background_task",
            "todo",
            "todo_read",
            "todo_write",
            "subagent"
    );

    private final Path workingDirectory;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final SkillRepository skillRepository;

    public RoomToolRegistryFactory(
            Path workingDirectory,
            ObjectMapper objectMapper,
            OkHttpClient httpClient,
            SkillRepository skillRepository
    ) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.skillRepository = Objects.requireNonNull(skillRepository);
    }

    /**
     * 根据 Agent 配置生成工具注册表。
     */
    public ToolRegistry create(RoomAgentProfile profile) {
        Set<String> allowlist = normalizedAllowlist(profile);
        ToolRegistry registry = new ToolRegistry(new LocalToolExecutor(objectMapper), new McpToolExecutor());
        if (allowed(allowlist, "read")) new ReadTool(workingDirectory).registerTo(registry);
        if (allowed(allowlist, "ls")) new LsTool(workingDirectory).registerTo(registry);
        if (allowed(allowlist, "glob")) new GlobTool(workingDirectory).registerTo(registry);
        if (allowed(allowlist, "grep")) new GrepTool(workingDirectory).registerTo(registry);
        if (allowed(allowlist, "web_fetch")) new WebFetchTool(workingDirectory, httpClient).registerTo(registry);
        if (allowed(allowlist, "web_search")) new WebSearchTool(workingDirectory, objectMapper, httpClient).registerTo(registry);
        if (allowed(allowlist, "load_skill")) new LoadSkillTool(workingDirectory, skillRepository).registerTo(registry);
        return registry;
    }

    private boolean allowed(Set<String> allowlist, String toolName) {
        return allowlist.contains(toolName) && !FORBIDDEN.contains(toolName);
    }

    private Set<String> normalizedAllowlist(RoomAgentProfile profile) {
        Set<String> result = new HashSet<>();
        if (profile.toolAllowlist() != null) {
            for (String tool : profile.toolAllowlist()) {
                if (tool != null && !tool.isBlank()) {
                    result.add(tool.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        result.removeAll(FORBIDDEN);
        return result;
    }
}
