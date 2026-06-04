package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * rooms.json 的顶层结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomIndexData(List<ChatRoom> rooms) {
    public RoomIndexData {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }
}
