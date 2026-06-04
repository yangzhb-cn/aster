package com.aster.app.room;

import com.aster.app.room.model.RoomAgentInput;
import com.aster.app.room.model.RoomAgentProfile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 房间 Agent 配置注册表。
 */
public interface RoomAgentRegistry {
    /**
     * 列出未归档 Agent。
     */
    List<RoomAgentProfile> listActive() throws IOException;

    /**
     * 列出全部 Agent，包含已归档记录。
     */
    List<RoomAgentProfile> listAll() throws IOException;

    /**
     * 列出已归档 Agent。
     */
    List<RoomAgentProfile> listArchived() throws IOException;

    /**
     * 列出启用且未归档 Agent。
     */
    List<RoomAgentProfile> listEnabled() throws IOException;

    /**
     * 读取 Agent 配置。
     */
    Optional<RoomAgentProfile> get(String agentId) throws IOException;

    /**
     * 创建 Agent。
     */
    RoomAgentProfile create(RoomAgentInput input) throws IOException;

    /**
     * 更新 Agent。
     */
    RoomAgentProfile update(RoomAgentInput input) throws IOException;

    /**
     * 归档 Agent。
     */
    RoomAgentProfile archive(String agentId) throws IOException;

    /**
     * 从归档恢复 Agent。
     */
    RoomAgentProfile restore(String agentId) throws IOException;

    /**
     * 物理删除 Agent 配置。
     */
    RoomAgentProfile deletePermanently(String agentId) throws IOException;
}
