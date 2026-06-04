package com.aster.app.room;

import com.aster.app.room.model.HubMessage;

import java.io.IOException;
import java.util.List;

/**
 * 房间共享消息存储接口。
 */
public interface RoomMessageStore {
    /**
     * 追加房间消息。
     */
    void append(HubMessage message) throws IOException;

    /**
     * 读取房间全部消息。
     */
    List<HubMessage> list(String roomId) throws IOException;

    /**
     * 读取最近 N 条消息。
     */
    List<HubMessage> recent(String roomId, int limit) throws IOException;

    /**
     * 物理删除房间消息文件。
     */
    void deleteRoom(String roomId) throws IOException;
}
