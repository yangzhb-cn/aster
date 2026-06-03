package com.aster.ui.im.telegram;

import com.aster.ui.im.telegram.model.TelegramSessionMapData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Telegram chat 到当前 Aster session 的映射存储。
 *
 * <p>它只保存当前选择，不保存对话内容。完整对话仍然写入 workspace/sessions/*.jsonl。</p>
 */
public class TelegramSessionMapStore {
    private final ObjectMapper objectMapper;
    private final Path file;

    public TelegramSessionMapStore(ObjectMapper objectMapper, Path file) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
    }

    /**
     * 读取指定 chat 当前绑定的 sessionId。
     */
    public synchronized Optional<String> get(long chatId) throws IOException {
        return Optional.ofNullable(load().sessions().get(String.valueOf(chatId)));
    }

    /**
     * 保存指定 chat 当前绑定的 sessionId。
     */
    public synchronized void put(long chatId, String sessionId) throws IOException {
        TelegramSessionMapData data = load();
        Map<String, String> sessions = new LinkedHashMap<>(data.sessions());
        sessions.put(String.valueOf(chatId), sessionId);
        save(new TelegramSessionMapData(sessions));
    }

    private TelegramSessionMapData load() throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            return TelegramSessionMapData.empty();
        }
        return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), TelegramSessionMapData.class);
    }

    private void save(TelegramSessionMapData data) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8
        );
    }
}
