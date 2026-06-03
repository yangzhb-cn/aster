package com.aster.app.todo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Web 便签待办项。
 *
 * <p>它保存当前状态，不是事件流水。dueAt 使用字符串，避免 Web JSON
 * 序列化额外依赖 JavaTimeModule。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TodoItem(
        String id,
        String content,
        TodoStatus status,
        String priority,
        String dueAt,
        String result,
        String createdAt,
        String updatedAt
) {
    /**
     * 创建待办项。
     */
    public static TodoItem create(String content, String priority, String dueAt) {
        String now = Instant.now().toString();
        return new TodoItem(
                "todo_" + UUID.randomUUID(),
                content,
                TodoStatus.PENDING,
                normalizePriority(priority),
                blankToNull(dueAt),
                null,
                now,
                now
        );
    }

    /**
     * 返回更新内容后的待办项。
     */
    public TodoItem updated(String nextContent, String nextPriority, String nextDueAt) {
        return new TodoItem(
                id,
                blankToDefault(nextContent, content),
                status,
                normalizePriority(blankToDefault(nextPriority, priority)),
                blankToNull(nextDueAt),
                result,
                createdAt,
                Instant.now().toString()
        );
    }

    /**
     * 返回完成后的待办项。
     */
    public TodoItem completed(String message) {
        return withStatus(TodoStatus.COMPLETED, message);
    }

    /**
     * 返回归档后的待办项。
     */
    public TodoItem archived() {
        return withStatus(TodoStatus.ARCHIVED, result);
    }

    private TodoItem withStatus(TodoStatus nextStatus, String message) {
        return new TodoItem(
                id,
                content,
                nextStatus,
                priority,
                dueAt,
                blankToNull(message),
                createdAt,
                Instant.now().toString()
        );
    }

    private static String normalizePriority(String value) {
        String text = blankToDefault(value, "medium").toLowerCase();
        return switch (text) {
            case "high", "medium", "low" -> text;
            default -> "medium";
        };
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
