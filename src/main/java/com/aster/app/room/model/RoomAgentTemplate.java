package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 房间 Agent 模板定义。
 *
 * <p>模板只用于首次初始化示例 Agent，不参与运行时业务逻辑。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomAgentTemplate(
        String name,
        String role,
        String description,
        String promptPath,
        List<String> mentionAliases,
        List<String> toolAllowlist,
        Boolean enabled
) {
    public RoomAgentTemplate {
        mentionAliases = mentionAliases == null ? List.of() : List.copyOf(mentionAliases);
        toolAllowlist = toolAllowlist == null ? List.of() : List.copyOf(toolAllowlist);
    }
}
