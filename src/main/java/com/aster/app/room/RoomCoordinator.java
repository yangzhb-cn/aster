package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomSendResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Web 聊天室协调器。
 *
 * <p>它负责把用户消息写入 hub message、解析 @Agent、依次触发被 @ 的 Agent，
 * 并把 Agent 最终回复再写回房间共享消息。</p>
 */
public class RoomCoordinator {
    private final RoomStore roomStore;
    private final RoomHub roomHub;
    private final RoomAgentRegistry agentRegistry;
    private final RoomMentionParser mentionParser;
    private final RoomAgentRunner agentRunner;

    public RoomCoordinator(
            RoomStore roomStore,
            RoomHub roomHub,
            RoomAgentRegistry agentRegistry,
            RoomMentionParser mentionParser,
            RoomAgentRunner agentRunner
    ) {
        this.roomStore = Objects.requireNonNull(roomStore);
        this.roomHub = Objects.requireNonNull(roomHub);
        this.agentRegistry = Objects.requireNonNull(agentRegistry);
        this.mentionParser = Objects.requireNonNull(mentionParser);
        this.agentRunner = Objects.requireNonNull(agentRunner);
    }

    /**
     * 发送一条房间消息，并同步返回被 @ Agent 的最终回复。
     */
    public synchronized RoomSendResult send(String roomId, String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IOException("text is required");
        }
        ChatRoom room = activeRoom(roomId);
        if (room.topic() == null || room.topic().isBlank()) {
            room = roomStore.updateTopic(room.roomId(), firstLine(text));
        }

        List<RoomAgentProfile> agents = mentionParser.parse(text, agentRegistry.listEnabled());
        List<String> mentionIds = agents.stream().map(RoomAgentProfile::agentId).toList();
        HubMessage userMessage = roomHub.publish(HubMessage.user(room.roomId(), text, mentionIds));

        List<HubMessage> emitted = new ArrayList<>();
        emitted.add(userMessage);
        for (RoomAgentProfile agent : agents) {
            String answer = agentRunner.run(room, agent, userMessage);
            HubMessage reply = roomHub.publish(HubMessage.agentReply(
                    room.roomId(),
                    userMessage.runId(),
                    userMessage.messageId(),
                    agent.agentId(),
                    agent.name(),
                    agent.role(),
                    answer == null ? "" : answer
            ));
            emitted.add(reply);
        }
        return new RoomSendResult(room, emitted);
    }

    /**
     * 读取房间全部共享消息。
     */
    public List<HubMessage> messages(String roomId) throws IOException {
        activeRoom(roomId);
        return roomHub.list(roomId);
    }

    private ChatRoom activeRoom(String roomId) throws IOException {
        try {
            RoomIdValidator.requireSafeId(roomId, "roomId");
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        ChatRoom room = roomStore.get(roomId)
                .orElseThrow(() -> new IOException("room not found: " + roomId));
        if (room.archived()) {
            throw new IOException("room archived: " + roomId);
        }
        return room;
    }

    private String firstLine(String text) {
        String normalized = text == null ? "" : text.strip();
        int lineBreak = normalized.indexOf('\n');
        if (lineBreak >= 0) {
            normalized = normalized.substring(0, lineBreak).strip();
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }
}
