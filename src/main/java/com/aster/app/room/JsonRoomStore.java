package com.aster.app.room;

import com.aster.app.room.model.ChatRoom;
import com.aster.app.room.model.RoomIndexData;
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
 * JSON 聊天室索引存储。
 *
 * <p>rooms.json 只保存房间元信息；消息内容由 JsonlRoomMessageStore 追加保存。</p>
 */
public class JsonRoomStore implements RoomStore {
    private final ObjectMapper objectMapper;
    private final Path file;

    public JsonRoomStore(ObjectMapper objectMapper, Path file) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            save(new RoomIndexData(List.of()));
        }
    }

    /**
     * 列出未归档房间，按更新时间倒序。
     */
    @Override
    public synchronized List<ChatRoom> listActive() throws IOException {
        return load().rooms().stream()
                .filter(room -> !room.archived())
                .sorted(Comparator.comparing(ChatRoom::updatedAt).reversed())
                .toList();
    }

    /**
     * 列出已归档房间，按更新时间倒序。
     */
    @Override
    public synchronized List<ChatRoom> listArchived() throws IOException {
        return load().rooms().stream()
                .filter(ChatRoom::archived)
                .sorted(Comparator.comparing(ChatRoom::updatedAt).reversed())
                .toList();
    }

    /**
     * 读取指定房间。
     */
    @Override
    public synchronized Optional<ChatRoom> get(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        return load().rooms().stream()
                .filter(room -> room.roomId().equals(roomId))
                .findFirst();
    }

    /**
     * 确保 Web 首次打开时有一个可用房间。
     */
    @Override
    public synchronized ChatRoom ensureDefault() throws IOException {
        List<ChatRoom> active = listActive();
        if (!active.isEmpty()) {
            return active.getFirst();
        }
        return create("默认聊天室");
    }

    /**
     * 创建房间。
     */
    @Override
    public synchronized ChatRoom create(String name) throws IOException {
        RoomIndexData data = load();
        List<ChatRoom> rooms = new ArrayList<>(data.rooms());
        ChatRoom room = ChatRoom.create(name);
        while (containsRoomId(rooms, room.roomId())) {
            room = ChatRoom.create(name);
        }
        rooms.add(room);
        save(new RoomIndexData(rooms));
        return room;
    }

    /**
     * 更新房间名称和主题。
     */
    @Override
    public synchronized ChatRoom update(String roomId, String name, String topic) throws IOException {
        return updateRoom(roomId, room -> room.updated(name, topic));
    }

    /**
     * 更新房间主题。
     */
    @Override
    public synchronized ChatRoom updateTopic(String roomId, String topic) throws IOException {
        return updateRoom(roomId, room -> room.withTopic(topic));
    }

    /**
     * 归档房间。
     */
    @Override
    public synchronized ChatRoom archive(String roomId) throws IOException {
        return updateRoom(roomId, ChatRoom::markArchived);
    }

    /**
     * 从归档恢复房间。
     */
    @Override
    public synchronized ChatRoom restore(String roomId) throws IOException {
        return updateRoom(roomId, ChatRoom::restored);
    }

    /**
     * 从 rooms.json 中物理删除房间元信息。
     */
    @Override
    public synchronized ChatRoom deletePermanently(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIndexData data = load();
        List<ChatRoom> rooms = new ArrayList<>();
        ChatRoom deleted = null;
        for (ChatRoom room : data.rooms()) {
            if (room.roomId().equals(roomId)) {
                deleted = room;
            } else {
                rooms.add(room);
            }
        }
        if (deleted == null) {
            throw new IOException("room not found: " + roomId);
        }
        if (!deleted.archived()) {
            throw new IOException("room must be archived before physical delete: " + roomId);
        }
        save(new RoomIndexData(rooms));
        return deleted;
    }

    private ChatRoom updateRoom(String roomId, Updater updater) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        RoomIndexData data = load();
        List<ChatRoom> rooms = new ArrayList<>();
        ChatRoom updated = null;
        for (ChatRoom room : data.rooms()) {
            if (room.roomId().equals(roomId)) {
                updated = updater.update(room);
                rooms.add(updated);
            } else {
                rooms.add(room);
            }
        }
        if (updated == null) {
            throw new IOException("room not found: " + roomId);
        }
        save(new RoomIndexData(rooms));
        return updated;
    }

    private RoomIndexData load() throws IOException {
        if (!Files.exists(file)) {
            return new RoomIndexData(List.of());
        }
        return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), RoomIndexData.class);
    }

    private boolean containsRoomId(List<ChatRoom> rooms, String roomId) {
        for (ChatRoom room : rooms) {
            if (room.roomId().equals(roomId)) {
                return true;
            }
        }
        return false;
    }

    private void save(RoomIndexData data) throws IOException {
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }

    @FunctionalInterface
    private interface Updater {
        ChatRoom update(ChatRoom room);
    }
}
