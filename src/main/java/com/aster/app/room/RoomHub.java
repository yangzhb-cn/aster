package com.aster.app.room;

import com.aster.app.room.model.HubMessage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 房间共享消息 Hub。
 *
 * <p>第一版只负责持久化和读取。后续如果要做实时广播，可以在这里挂订阅者，
 * WebSocket/SSE/IM 都消费同一份 hub message。</p>
 */
public class RoomHub {
    private final RoomMessageStore messageStore;

    public RoomHub(RoomMessageStore messageStore) {
        this.messageStore = Objects.requireNonNull(messageStore);
    }

    /**
     * 发布一条房间消息。
     */
    public HubMessage publish(HubMessage message) throws IOException {
        messageStore.append(message);
        return message;
    }

    /**
     * 读取房间全部消息。
     */
    public List<HubMessage> list(String roomId) throws IOException {
        return messageStore.list(roomId);
    }

    /**
     * 读取房间最近消息，用于注入 Agent 私有上下文。
     */
    public List<HubMessage> recent(String roomId, int limit) throws IOException {
        return messageStore.recent(roomId, limit);
    }

    /**
     * 物理删除房间共享消息。
     */
    public void deleteRoom(String roomId) throws IOException {
        messageStore.deleteRoom(roomId);
    }
}
