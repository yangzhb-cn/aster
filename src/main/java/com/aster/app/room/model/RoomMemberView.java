package com.aster.app.room.model;

/**
 * Web 展示用的聊天室成员视图。
 *
 * <p>membership 保存成员关系状态，agent 保存全局 Agent 配置。</p>
 */
public record RoomMemberView(RoomMembership membership, RoomAgentProfile agent) {
}
