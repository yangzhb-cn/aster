package com.aster.app.room;

import com.aster.app.room.model.HubMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JSONL 房间消息存储。
 *
 * <p>每个房间一个 JSONL 文件，追加写入 hub message，方便审计和按房间回放。</p>
 */
public class JsonlRoomMessageStore implements RoomMessageStore {
    private final ObjectMapper objectMapper;
    private final Path directory;

    public JsonlRoomMessageStore(ObjectMapper objectMapper, Path directory) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.directory = Objects.requireNonNull(directory);
        Files.createDirectories(directory);
    }

    /**
     * 追加一条共享消息。
     */
    @Override
    public synchronized void append(HubMessage message) throws IOException {
        Objects.requireNonNull(message);
        RoomIdValidator.requireSafeId(message.roomId(), "roomId");
        Files.writeString(
                fileFor(message.roomId()),
                objectMapper.writeValueAsString(message) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    /**
     * 读取房间全部消息。
     */
    @Override
    public synchronized List<HubMessage> list(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        Path file = fileFor(roomId);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<HubMessage> messages = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                messages.add(objectMapper.readValue(line, HubMessage.class));
            }
        }
        return messages;
    }

    /**
     * 读取房间最近 N 条消息。
     */
    @Override
    public synchronized List<HubMessage> recent(String roomId, int limit) throws IOException {
        List<HubMessage> messages = list(roomId);
        if (limit <= 0 || messages.size() <= limit) {
            return messages;
        }
        return messages.subList(messages.size() - limit, messages.size());
    }

    /**
     * 删除房间共享消息 JSONL 文件。
     */
    @Override
    public synchronized void deleteRoom(String roomId) throws IOException {
        RoomIdValidator.requireSafeId(roomId, "roomId");
        Files.deleteIfExists(fileFor(roomId));
    }

    private Path fileFor(String roomId) {
        return directory.resolve(roomId + ".jsonl").normalize();
    }
}
