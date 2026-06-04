package com.aster.app.room;

import com.aster.app.room.model.RoomAgentProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 解析聊天室里的 @Agent 指令。
 */
public class RoomMentionParser {
    /**
     * 从用户消息中解析被 @ 的 Agent。
     *
     * <p>支持 @all、@所有、@名称 和 @别名。返回顺序按注册表传入顺序去重。</p>
     */
    public List<RoomAgentProfile> parse(String text, List<RoomAgentProfile> agents) {
        if (text == null || text.isBlank() || agents == null || agents.isEmpty()) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<RoomAgentProfile> enabledAgents = agents.stream()
                .filter(RoomAgentProfile::enabled)
                .filter(agent -> !agent.archived())
                .toList();
        if (containsMention(normalized, "all") || containsMention(normalized, "所有")) {
            return enabledAgents;
        }

        Set<String> selectedIds = new LinkedHashSet<>();
        for (RoomAgentProfile agent : enabledAgents) {
            for (String alias : agent.mentionAliases()) {
                if (alias != null && containsMention(normalized, alias.toLowerCase(Locale.ROOT))) {
                    selectedIds.add(agent.agentId());
                    break;
                }
            }
        }

        List<RoomAgentProfile> selected = new ArrayList<>();
        for (RoomAgentProfile agent : enabledAgents) {
            if (selectedIds.contains(agent.agentId())) {
                selected.add(agent);
            }
        }
        return selected;
    }

    private boolean containsMention(String text, String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        String needle = "@" + alias.trim().toLowerCase(Locale.ROOT);
        int index = text.indexOf(needle);
        while (index >= 0) {
            int after = index + needle.length();
            if (after >= text.length() || isMentionBoundary(text.charAt(after))) {
                return true;
            }
            index = text.indexOf(needle, index + 1);
        }
        return false;
    }

    private boolean isMentionBoundary(char value) {
        return Character.isWhitespace(value)
                || value == ':'
                || value == '：'
                || value == ','
                || value == '，'
                || value == '.'
                || value == '。'
                || value == ';'
                || value == '；'
                || value == ')'
                || value == '）';
    }
}
