package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 房间 Agent 配置。
 *
 * <p>name、role、description 用于展示和 mention；真正的系统提示词放在外部 Markdown 文件中，
 * 这样 Web 可以修改角色，而不是把一组开发角色硬编码在 Java 里。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RoomAgentProfile(
        String agentId,
        String name,
        String role,
        String description,
        String systemPromptPath,
        List<String> mentionAliases,
        List<String> toolAllowlist,
        boolean enabled,
        boolean archived,
        String createdAt,
        String updatedAt
) {
    public RoomAgentProfile {
        mentionAliases = mentionAliases == null ? List.of() : List.copyOf(mentionAliases);
        toolAllowlist = toolAllowlist == null ? List.of() : List.copyOf(toolAllowlist);
    }

    /**
     * 创建新 Agent 配置。
     */
    public static RoomAgentProfile create(
            String name,
            String role,
            String description,
            String systemPromptPath,
            List<String> aliases,
            List<String> toolAllowlist,
            boolean enabled
    ) {
        String now = Instant.now().toString();
        return new RoomAgentProfile(
                "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                clean(name, "Agent"),
                clean(role, "通用助手"),
                clean(description, ""),
                clean(systemPromptPath, ""),
                aliases,
                toolAllowlist,
                enabled,
                false,
                now,
                now
        );
    }

    /**
     * 返回更新后的配置。
     */
    public RoomAgentProfile updated(
            String nextName,
            String nextRole,
            String nextDescription,
            String nextSystemPromptPath,
            List<String> nextAliases,
            List<String> nextToolAllowlist,
            boolean nextEnabled
    ) {
        return new RoomAgentProfile(
                agentId,
                clean(nextName, name),
                clean(nextRole, role),
                clean(nextDescription, description),
                clean(nextSystemPromptPath, systemPromptPath),
                nextAliases,
                nextToolAllowlist,
                nextEnabled,
                archived,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回归档后的配置。
     */
    public RoomAgentProfile markArchived() {
        return new RoomAgentProfile(
                agentId,
                name,
                role,
                description,
                systemPromptPath,
                mentionAliases,
                toolAllowlist,
                enabled,
                true,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回恢复为未归档后的配置。
     */
    public RoomAgentProfile restored() {
        return new RoomAgentProfile(
                agentId,
                name,
                role,
                description,
                systemPromptPath,
                mentionAliases,
                toolAllowlist,
                enabled,
                false,
                createdAt,
                Instant.now().toString()
        );
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
