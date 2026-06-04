package com.aster.app.room.model;

import java.util.List;

/**
 * 房间发送消息后的结果。
 */
public record RoomSendResult(
        ChatRoom room,
        List<HubMessage> messages
) {
}
