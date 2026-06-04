package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * agents.json 的顶层结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomAgentIndexData(List<RoomAgentProfile> agents) {
    public RoomAgentIndexData {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }
}
