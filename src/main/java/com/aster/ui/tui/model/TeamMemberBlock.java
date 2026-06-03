package com.aster.ui.tui.model;

/**
 * Agent Team 成员的流式输出块。
 */
public final class TeamMemberBlock implements UiBlock {
    private final String taskId;
    private final String role;
    private final StringBuilder text = new StringBuilder();

    public TeamMemberBlock(String taskId, String role) {
        this.taskId = taskId;
        this.role = role;
    }

    public String taskId() {
        return taskId;
    }

    public String role() {
        return role;
    }

    public String text() {
        return text.toString();
    }

    /**
     * 追加一段成员流式正文。
     */
    public void append(String value) {
        text.append(value);
    }
}
