package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Web 创建或更新房间 Agent 的输入。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomAgentInput(
        String agentId,
        String name,
        String role,
        String description,
        String systemPrompt,
        List<String> mentionAliases,
        List<String> toolAllowlist,
        String model,
        Boolean enabled
) {
}
