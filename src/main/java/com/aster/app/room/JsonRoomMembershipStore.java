package com.aster.app.room;

import com.aster.app.room.model.RoomAgentProfile;
import com.aster.app.room.model.RoomMembership;
import com.aster.app.room.model.RoomMembershipIndexData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON 聊天室成员关系存储。
 *
 * <p>members.json 只保存房间和 Agent 的关联关系。Agent 定义仍在 agents.json，
 * 房间消息仍在 messages/*.jsonl。</p>
 */
public class JsonRoomMembershipStore implements RoomMembershipStore {
    private final ObjectMapper objectMapper;
    private final Path file;

    public JsonRoomMembershipStore(ObjectMapper objectMapper, Path file) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            save(new RoomMembershipIndexData(List.of()));
        }
    }

    /**
     * 首次使用房间时，把当前全局 Agent 初始化为房间成员。
     */
    @Override
    public synchronized List<RoomMembership> ensureRoomMembers(String roomId, List<RoomAgentProfile> agents) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        if (!listAll(roomId).isEmpty()) {
            return listActive(roomId);
        }

        RoomMembershipIndexData data = load();
        List<RoomMembership> memberships = new ArrayList<>(data.memberships());
        int orderIndex = 0;
        for (RoomAgentProfile agent : agents == null ? List.<RoomAgentProfile>of() : agents) {
            if (agent == null || agent.archived()) {
                continue;
            }
            memberships.add(RoomMembership.create(roomId, agent.agentId(), orderIndex++));
        }
        save(new RoomMembershipIndexData(memberships));
        return listActive(roomId);
    }

    /**
     * 列出未归档成员，按 orderIndex 升序。
     */
    @Override
    public synchronized List<RoomMembership> listActive(String roomId) throws IOException {
        return listAll(roomId).stream()
                .filter(member -> !member.archived())
                .toList();
    }

    /**
     * 列出已归档成员，按 orderIndex 升序。
     */
    @Override
    public synchronized List<RoomMembership> listArchived(String roomId) throws IOException {
        return listAll(roomId).stream()
                .filter(RoomMembership::archived)
                .toList();
    }

    /**
     * 读取指定成员关系。
     */
    @Override
    public synchronized Optional<RoomMembership> get(String roomId, String agentId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIdValidator.requireSafeId(agentId, "agentId");
        return load().memberships().stream()
                .filter(member -> member.roomId().equals(roomId) && member.agentId().equals(agentId))
                .findFirst();
    }

    /**
     * 把 Agent 加入聊天室。
     */
    @Override
    public synchronized RoomMembership add(String roomId, String agentId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIdValidator.requireSafeId(agentId, "agentId");
        Optional<RoomMembership> existing = get(roomId, agentId);
        if (existing.isPresent()) {
            if (existing.get().archived()) {
                return restore(roomId, agentId);
            }
            return existing.get();
        }

        RoomMembershipIndexData data = load();
        List<RoomMembership> memberships = new ArrayList<>(data.memberships());
        RoomMembership created = RoomMembership.create(roomId, agentId, nextOrderIndex(roomId, memberships));
        memberships.add(created);
        save(new RoomMembershipIndexData(memberships));
        return created;
    }

    /**
     * 从聊天室移除 Agent。
     */
    @Override
    public synchronized RoomMembership archive(String roomId, String agentId) throws IOException {
        return update(roomId, agentId, RoomMembership::markArchived);
    }

    /**
     * 恢复已移除的 Agent，并递增 generation。
     */
    @Override
    public synchronized RoomMembership restore(String roomId, String agentId) throws IOException {
        return update(roomId, agentId, RoomMembership::restored);
    }

    /**
     * 删除某个房间的全部成员关系。
     */
    @Override
    public synchronized void deleteRoom(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomMembershipIndexData data = load();
        save(new RoomMembershipIndexData(data.memberships().stream()
                .filter(member -> !member.roomId().equals(roomId))
                .toList()));
    }

    /**
     * 删除某个 Agent 的全部成员关系。
     */
    @Override
    public synchronized void deleteAgent(String agentId) throws IOException {
        RoomIdValidator.requireSafeId(agentId, "agentId");
        RoomMembershipIndexData data = load();
        save(new RoomMembershipIndexData(data.memberships().stream()
                .filter(member -> !member.agentId().equals(agentId))
                .toList()));
    }

    private List<RoomMembership> listAll(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        return load().memberships().stream()
                .filter(member -> member.roomId().equals(roomId))
                .sorted(Comparator.comparingInt(RoomMembership::orderIndex))
                .toList();
    }

    private RoomMembership update(String roomId, String agentId, Updater updater) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIdValidator.requireSafeId(agentId, "agentId");
        RoomMembershipIndexData data = load();
        List<RoomMembership> memberships = new ArrayList<>();
        RoomMembership updated = null;
        for (RoomMembership member : data.memberships()) {
            if (member.roomId().equals(roomId) && member.agentId().equals(agentId)) {
                updated = updater.update(member);
                memberships.add(updated);
            } else {
                memberships.add(member);
            }
        }
        if (updated == null) {
            throw new IOException("room member not found: " + roomId + " " + agentId);
        }
        save(new RoomMembershipIndexData(memberships));
        return updated;
    }

    private int nextOrderIndex(String roomId, List<RoomMembership> memberships) {
        return memberships.stream()
                .filter(member -> member.roomId().equals(roomId))
                .mapToInt(RoomMembership::orderIndex)
                .max()
                .orElse(-1) + 1;
    }

    private RoomMembershipIndexData load() throws IOException {
        if (!Files.exists(file)) {
            return new RoomMembershipIndexData(List.of());
        }
        return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), RoomMembershipIndexData.class);
    }

    private void save(RoomMembershipIndexData data) throws IOException {
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }

    @FunctionalInterface
    private interface Updater {
        RoomMembership update(RoomMembership membership);
    }
}
