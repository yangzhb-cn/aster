package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 房间 Agent 模板 JSON 顶层结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomAgentTemplateData(List<RoomAgentTemplate> agents) {
    public RoomAgentTemplateData {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }
}
