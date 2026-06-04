package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 房间共享消息。
 *
 * <p>它只保存用户可见的聊天室内容：用户发言、Agent 最终回复和系统提示。
 * 工具调用、工具结果、reasoning token 不进入 hub message，避免房间流被实现细节污染。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HubMessage(
        String roomId,
        String messageId,
        String runId,
        String parentMessageId,
        SpeakerType speakerType,
        String speakerId,
        String speakerName,
        String speakerRole,
        HubMessageType type,
        String content,
        List<String> mentions,
        Integer replyIndex,
        String createdAt
) {
    public HubMessage {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }

    /**
     * 创建用户发言消息。
     */
    public static HubMessage user(String roomId, String content, List<String> mentions) {
        return new HubMessage(
                roomId,
                nextId("msg"),
                UUID.randomUUID().toString(),
                null,
                SpeakerType.USER,
                "web-user",
                "User",
                null,
                HubMessageType.CHAT,
                requireContent(content),
                mentions,
                null,
                now()
        );
    }

    /**
     * 创建 Agent 最终回复消息。
     */
    public static HubMessage agentReply(
            String roomId,
            String runId,
            String parentMessageId,
            String agentId,
            String agentName,
            String agentRole,
            String content,
            Integer replyIndex
    ) {
        return new HubMessage(
                roomId,
                nextId("msg"),
                runId,
                parentMessageId,
                SpeakerType.AGENT,
                agentId,
                agentName,
                agentRole,
                HubMessageType.CHAT,
                requireContent(content),
                List.of(),
                replyIndex,
                now()
        );
    }

    /**
     * 创建不带顺序号的 Agent 回复消息。
     */
    public static HubMessage agentReply(
            String roomId,
            String runId,
            String parentMessageId,
            String agentId,
            String agentName,
            String agentRole,
            String content
    ) {
        return agentReply(roomId, runId, parentMessageId, agentId, agentName, agentRole, content, null);
    }

    /**
     * 创建房间系统提示消息。
     */
    public static HubMessage system(String roomId, String content) {
        return new HubMessage(
                roomId,
                nextId("msg"),
                UUID.randomUUID().toString(),
                null,
                SpeakerType.SYSTEM,
                "system",
                "System",
                null,
                HubMessageType.SYSTEM,
                requireContent(content),
                List.of(),
                null,
                now()
        );
    }

    private static String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String now() {
        return Instant.now().toString();
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content.trim();
    }
}
