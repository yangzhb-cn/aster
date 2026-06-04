package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 聊天室成员关系。
 *
 * <p>它表示某个全局 Room Agent 是否属于某个聊天室。generation 用来切分该成员在聊天室里的
 * 私有上下文：移除后再恢复会进入下一代 session，从而避免继续使用旧上下文。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RoomMembership(
        String roomId,
        String agentId,
        int orderIndex,
        int generation,
        boolean archived,
        String createdAt,
        String updatedAt
) {
    /**
     * 创建聊天室成员关系。
     */
    public static RoomMembership create(String roomId, String agentId, int orderIndex) {
        String now = Instant.now().toString();
        return new RoomMembership(roomId, agentId, orderIndex, 1, false, now, now);
    }

    /**
     * 返回归档后的成员关系。
     */
    public RoomMembership markArchived() {
        return new RoomMembership(roomId, agentId, orderIndex, generation, true, createdAt, Instant.now().toString());
    }

    /**
     * 返回恢复后的成员关系。
     *
     * <p>恢复会递增 generation，后续 RoomAgentSessionFactory 会打开新的私有 JSONL session。</p>
     */
    public RoomMembership restored() {
        return new RoomMembership(roomId, agentId, orderIndex, generation + 1, false, createdAt, Instant.now().toString());
    }
}
