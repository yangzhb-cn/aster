package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentProfile;

import java.util.List;

/**
 * 单个房间 Agent 执行时的临时上下文。
 *
 * <p>它包含房间主题、当前被回复的消息和共享消息快照，只在本轮 LLM 请求前注入，
 * 不写入 Agent 私有 session。</p>
 */
public record RoomAgentRunContext(
        ChatRoom room,
        RoomAgentProfile agent,
        HubMessage triggerMessage,
        List<HubMessage> recentMessages
) {
    public RoomAgentRunContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}
