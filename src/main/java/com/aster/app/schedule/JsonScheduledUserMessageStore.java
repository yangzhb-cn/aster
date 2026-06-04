package com.aster.app.schedule;

import com.aster.app.schedule.model.ScheduleDocument;
import com.aster.app.schedule.model.ScheduleStatus;
import com.aster.app.schedule.model.ScheduledUserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON 自动化用户消息存储。
 *
 * <p>schedules.json 保存当前 schedule 状态，适合被工具增删改查和调度器读取。</p>
 */
public class JsonScheduledUserMessageStore implements ScheduledUserMessageStore {
    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Object lock;

    public JsonScheduledUserMessageStore(ObjectMapper objectMapper, Path file) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        this.lock = FILE_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new Object());
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            writeAll(List.of());
        }
    }

    /**
     * 列出全部 schedule。
     */
    @Override
    public List<ScheduledUserMessage> listAll() throws IOException {
        synchronized (lock) {
            return readAll();
        }
    }

    /**
     * 列出指定 session 下仍启用的 schedule。
     */
    @Override
    public List<ScheduledUserMessage> listActive(String sessionId) throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(schedule -> Objects.equals(schedule.sessionId(), sessionId))
                    .filter(schedule -> schedule.enabled() && schedule.status() == ScheduleStatus.ACTIVE)
                    .toList();
        }
    }

    /**
     * 创建 schedule。
     */
    @Override
    public ScheduledUserMessage create(ScheduledUserMessage schedule) throws IOException {
        synchronized (lock) {
            List<ScheduledUserMessage> schedules = new ArrayList<>(readAll());
            schedules.add(schedule);
            writeAll(schedules);
            return schedule;
        }
    }

    /**
     * 更新 schedule。
     */
    @Override
    public ScheduledUserMessage update(ScheduledUserMessage schedule) throws IOException {
        synchronized (lock) {
            List<ScheduledUserMessage> schedules = new ArrayList<>(readAll());
            int index = indexOf(schedules, schedule.id());
            schedules.set(index, schedule);
            writeAll(schedules);
            return schedule;
        }
    }

    /**
     * 查找 schedule。
     */
    @Override
    public Optional<ScheduledUserMessage> find(String scheduleId) throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(schedule -> Objects.equals(schedule.id(), scheduleId))
                    .findFirst();
        }
    }

    private List<ScheduledUserMessage> readAll() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        ScheduleDocument document = objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), ScheduleDocument.class);
        return document.schedules();
    }

    private void writeAll(List<ScheduledUserMessage> schedules) throws IOException {
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ScheduleDocument(schedules)),
                StandardCharsets.UTF_8
        );
    }

    private int indexOf(List<ScheduledUserMessage> schedules, String scheduleId) throws IOException {
        for (int i = 0; i < schedules.size(); i++) {
            if (Objects.equals(schedules.get(i).id(), scheduleId)) {
                return i;
            }
        }
        throw new IOException("schedule not found: " + scheduleId);
    }
}
