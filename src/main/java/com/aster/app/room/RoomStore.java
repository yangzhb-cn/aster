package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 聊天室元信息存储接口。
 */
public interface RoomStore {
    /**
     * 列出未归档房间。
     */
    List<ChatRoom> listActive() throws IOException;

    /**
     * 列出已归档房间。
     */
    List<ChatRoom> listArchived() throws IOException;

    /**
     * 读取房间。
     */
    Optional<ChatRoom> get(String roomId) throws IOException;

    /**
     * 确保至少存在一个默认房间。
     */
    ChatRoom ensureDefault() throws IOException;

    /**
     * 创建房间。
     */
    ChatRoom create(String name) throws IOException;

    /**
     * 更新房间名称和主题。
     */
    ChatRoom update(String roomId, String name, String topic) throws IOException;

    /**
     * 更新房间主题。
     */
    ChatRoom updateTopic(String roomId, String topic) throws IOException;

    /**
     * 归档房间。
     */
    ChatRoom archive(String roomId) throws IOException;

    /**
     * 从归档恢复房间。
     */
    ChatRoom restore(String roomId) throws IOException;

    /**
     * 物理删除房间元信息。
     */
    ChatRoom deletePermanently(String roomId) throws IOException;
}
