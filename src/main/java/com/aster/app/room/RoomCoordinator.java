package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.HubMessage;
import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMembership;
import com.aster.app.room.model.RoomSendResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Web 聊天室协调器。
 *
 * <p>它负责把用户消息写入 hub message、解析 @Agent、依次触发被 @ 的 Agent，
 * 并把 Agent 最终回复再写回房间共享消息。</p>
 */
public class RoomCoordinator {
    private static final int MAX_PARALLEL_AGENTS = 5;

    private final RoomStore roomStore;
    private final RoomHub roomHub;
    private final RoomAgentRegistry agentRegistry;
    private final RoomMembershipStore membershipStore;
    private final RoomMentionParser mentionParser;
    private final RoomAgentRunner agentRunner;

    public RoomCoordinator(
            RoomStore roomStore,
            RoomHub roomHub,
            RoomAgentRegistry agentRegistry,
            RoomMembershipStore membershipStore,
            RoomMentionParser mentionParser,
            RoomAgentRunner agentRunner
    ) {
        this.roomStore = Objects.requireNonNull(roomStore);
        this.roomHub = Objects.requireNonNull(roomHub);
        this.agentRegistry = Objects.requireNonNull(agentRegistry);
        this.membershipStore = Objects.requireNonNull(membershipStore);
        this.mentionParser = Objects.requireNonNull(mentionParser);
        this.agentRunner = Objects.requireNonNull(agentRunner);
    }

    /**
     * 发送一条房间消息，并同步返回被 @ Agent 的最终回复。
     *
     * <p>用户消息会先写入房间。被 @ 的 Agent 并行执行，但回复会按聊天室成员
     * orderIndex 生成的 replyIndex 稳定写回，避免“谁先完成谁先出现”的随机顺序。</p>
     */
    public synchronized RoomSendResult send(String roomId, String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IOException("text is required");
        }
        ChatRoom room = activeRoom(roomId);
        if (room.topic() == null || room.topic().isBlank()) {
            room = roomStore.updateTopic(room.roomId(), firstLine(text));
        }

        List<RoomAgentTarget> roomAgents = orderedRoomAgents(room.roomId());
        List<RoomAgentProfile> agents = mentionParser.parse(
                text,
                roomAgents.stream().map(RoomAgentTarget::agent).toList()
        );
        Map<String, RoomAgentTarget> targetByAgentId = new HashMap<>();
        for (RoomAgentTarget target : roomAgents) {
            targetByAgentId.put(target.agent().agentId(), target);
        }
        List<RoomAgentTarget> selectedTargets = agents.stream()
                .map(agent -> targetByAgentId.get(agent.agentId()))
                .filter(Objects::nonNull)
                .toList();
        List<String> mentionIds = selectedTargets.stream().map(target -> target.agent().agentId()).toList();
        HubMessage userMessage = roomHub.publish(HubMessage.user(room.roomId(), text, mentionIds));

        List<HubMessage> emitted = new ArrayList<>();
        emitted.add(userMessage);
        for (RoomAgentReply agentReply : runAgents(room, selectedTargets, userMessage)) {
            RoomAgentProfile agent = agentReply.target().agent();
            HubMessage hubReply = roomHub.publish(HubMessage.agentReply(
                    room.roomId(),
                    userMessage.runId(),
                    userMessage.messageId(),
                    agent.agentId(),
                    agent.name(),
                    agent.role(),
                    agentReply.text(),
                    agentReply.target().replyIndex()
            ));
            emitted.add(hubReply);
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

    /**
     * 当前房间可参与回复的 Agent，顺序来自成员关系 orderIndex。
     */
    private List<RoomAgentTarget> orderedRoomAgents(String roomId) throws IOException {
        List<RoomAgentProfile> activeAgents = agentRegistry.listActive();
        List<RoomMembership> memberships = membershipStore.ensureRoomMembers(roomId, activeAgents);
        Map<String, RoomAgentProfile> agentById = new HashMap<>();
        for (RoomAgentProfile agent : activeAgents) {
            agentById.put(agent.agentId(), agent);
        }

        List<RoomAgentTarget> targets = new ArrayList<>();
        int replyIndex = 0;
        for (RoomMembership membership : memberships) {
            RoomAgentProfile agent = agentById.get(membership.agentId());
            if (agent == null || !agent.enabled() || agent.archived()) {
                continue;
            }
            targets.add(new RoomAgentTarget(agent, membership, replyIndex++));
        }
        return targets;
    }

    /**
     * 并行执行被 @ 的 Agent，并按 replyIndex 返回结果。
     */
    private List<RoomAgentReply> runAgents(ChatRoom room, List<RoomAgentTarget> targets, HubMessage userMessage) throws IOException {
        if (targets.isEmpty()) {
            return List.of();
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(targets.size(), MAX_PARALLEL_AGENTS));
        try {
            List<Callable<RoomAgentReply>> tasks = targets.stream()
                    .map(target -> (Callable<RoomAgentReply>) () -> runAgent(room, target, userMessage))
                    .toList();
            List<Future<RoomAgentReply>> futures = executor.invokeAll(tasks);
            List<RoomAgentReply> replies = new ArrayList<>();
            for (Future<RoomAgentReply> future : futures) {
                try {
                    replies.add(future.get());
                } catch (Exception e) {
                    throw new IOException("room agent execution failed", e);
                }
            }
            replies.sort(Comparator.comparingInt(reply -> reply.target().replyIndex()));
            return replies;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("room agent execution interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private RoomAgentReply runAgent(ChatRoom room, RoomAgentTarget target, HubMessage userMessage) {
        try {
            String answer = agentRunner.run(room, target.agent(), target.membership(), userMessage);
            return new RoomAgentReply(target, answer == null ? "" : answer);
        } catch (Exception e) {
            return new RoomAgentReply(target, "本次回复失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
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

    private record RoomAgentTarget(RoomAgentProfile agent, RoomMembership membership, int replyIndex) {
    }

    private record RoomAgentReply(RoomAgentTarget target, String text) {
    }
}
