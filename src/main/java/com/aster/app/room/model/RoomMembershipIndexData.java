package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 聊天室成员索引文件结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomMembershipIndexData(List<RoomMembership> memberships) {
    public RoomMembershipIndexData {
        memberships = memberships == null ? List.of() : List.copyOf(memberships);
    }
}
