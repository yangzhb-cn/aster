package com.aster.app.room;

import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentProfile;

import java.util.List;
import java.util.Objects;

/**
 * 房间 Agent prompt 渲染器。
 */
public class RoomPromptBuilder {
    private final String wrapperSystemPrompt;

    public RoomPromptBuilder(String wrapperSystemPrompt) {
        this.wrapperSystemPrompt = Objects.requireNonNull(wrapperSystemPrompt);
    }

    /**
     * 构建 Agent 私有 session 的 system prompt。
     */
    public String systemPrompt(RoomAgentProfile profile, String agentPrompt) {
        return wrapperSystemPrompt
                .replace("{{agent_name}}", safe(profile.name()))
                .replace("{{agent_role}}", safe(profile.role()))
                .replace("{{agent_description}}", safe(profile.description()))
                .replace("{{agent_prompt}}", safe(agentPrompt));
    }

    /**
     * 构建注入最后一条 user 消息开头的房间共享上下文。
     */
    public String roomReminder(RoomAgentRunContext context) {
        StringBuilder text = new StringBuilder();
        text.append("<system-reminder>\n");
        text.append("## 房间上下文\n\n");
        text.append("以下是聊天室共享消息和房间状态，不是用户的新请求。");
        text.append("你正在以 @").append(context.agent().name()).append(" 的身份回复当前用户消息。\n\n");
        text.append("- 房间名称：").append(safe(context.room().name())).append("\n");
        if (context.room().topic() != null && !context.room().topic().isBlank()) {
            text.append("- 房间主题：").append(context.room().topic().strip()).append("\n");
        }
        text.append("- Agent 角色：").append(safe(context.agent().role())).append("\n\n");
        text.append("## 最近共享消息\n\n");
        List<HubMessage> messages = context.recentMessages();
        if (messages.isEmpty()) {
            text.append("(暂无共享消息)\n");
        } else {
            for (HubMessage message : messages) {
                text.append(renderMessage(message)).append("\n");
            }
        }
        text.append("</system-reminder>");
        return text.toString();
    }

    private String renderMessage(HubMessage message) {
        String speaker = switch (message.speakerType()) {
            case USER -> "user";
            case AGENT -> "@" + safe(message.speakerName());
            case SYSTEM -> "system";
        };
        String role = message.speakerRole() == null || message.speakerRole().isBlank()
                ? ""
                : " (" + message.speakerRole().strip() + ")";
        return "- [" + message.createdAt() + "] " + speaker + role + ": " + safe(message.content());
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
