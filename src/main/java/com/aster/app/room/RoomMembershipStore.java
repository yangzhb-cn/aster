package com.aster.app.room;

import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMembership;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 聊天室成员关系存储接口。
 */
public interface RoomMembershipStore {
    /**
     * 确保房间首次使用时有一组默认成员。
     */
    List<RoomMembership> ensureRoomMembers(String roomId, List<RoomAgentProfile> agents) throws IOException;

    /**
     * 列出当前房间未归档成员。
     */
    List<RoomMembership> listActive(String roomId) throws IOException;

    /**
     * 列出当前房间已移除成员。
     */
    List<RoomMembership> listArchived(String roomId) throws IOException;

    /**
     * 读取某个房间成员关系。
     */
    Optional<RoomMembership> get(String roomId, String agentId) throws IOException;

    /**
     * 把 Agent 加入聊天室。
     */
    RoomMembership add(String roomId, String agentId) throws IOException;

    /**
     * 从聊天室移除 Agent。
     */
    RoomMembership archive(String roomId, String agentId) throws IOException;

    /**
     * 恢复已移除的聊天室 Agent。
     */
    RoomMembership restore(String roomId, String agentId) throws IOException;

    /**
     * 物理删除某个房间的全部成员关系。
     */
    void deleteRoom(String roomId) throws IOException;

    /**
     * 物理删除某个 Agent 的全部成员关系。
     */
    void deleteAgent(String agentId) throws IOException;
}
