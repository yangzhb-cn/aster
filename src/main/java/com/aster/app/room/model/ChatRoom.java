package com.aster.app.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Web 多 Agent 聊天室元信息。
 *
 * <p>房间只记录名称、主题和归档状态；具体消息保存在独立 JSONL 文件中。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatRoom(
        String roomId,
        String name,
        String topic,
        String createdAt,
        String updatedAt,
        boolean archived
) {
    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    /**
     * 创建房间。
     */
    public static ChatRoom create(String name) {
        String now = Instant.now().toString();
        return new ChatRoom(
                "room_" + ID_TIME_FORMATTER.format(Instant.now()) + "_" + randomSuffix(),
                clean(name, "聊天室 " + now),
                "",
                now,
                now,
                false
        );
    }

    /**
     * 返回更新名称和主题后的房间。
     */
    public ChatRoom updated(String nextName, String nextTopic) {
        return new ChatRoom(
                roomId,
                clean(nextName, name),
                nextTopic == null ? topic : nextTopic.trim(),
                createdAt,
                Instant.now().toString(),
                archived
        );
    }

    /**
     * 返回只刷新主题后的房间。
     */
    public ChatRoom withTopic(String nextTopic) {
        return new ChatRoom(
                roomId,
                name,
                nextTopic == null ? "" : nextTopic.trim(),
                createdAt,
                Instant.now().toString(),
                archived
        );
    }

    /**
     * 返回归档后的房间。
     */
    public ChatRoom markArchived() {
        return new ChatRoom(roomId, name, topic, createdAt, Instant.now().toString(), true);
    }

    /**
     * 返回恢复为未归档后的房间。
     */
    public ChatRoom restored() {
        return new ChatRoom(roomId, name, topic, createdAt, Instant.now().toString(), false);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
