package com.aster.app.todo;

import com.aster.app.todo.model.TodoDocument;
import com.aster.app.todo.model.TodoItem;
import com.aster.app.todo.model.TodoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON 便签待办存储。
 *
 * <p>todos.json 保存当前清单状态，适合 Web 直接编辑和后台扫描。</p>
 */
public class JsonTodoStore implements TodoStore {
    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Object lock;

    public JsonTodoStore(ObjectMapper objectMapper, Path file) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.file = Objects.requireNonNull(file);
        this.lock = FILE_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new Object());
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            writeAll(List.of());
        }
    }

    /**
     * 列出全部非归档待办。
     */
    @Override
    public List<TodoItem> listActive() throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(item -> item.status() != TodoStatus.ARCHIVED)
                    .toList();
        }
    }

    /**
     * 列出全部已归档待办。
     */
    @Override
    public List<TodoItem> listArchived() throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(item -> item.status() == TodoStatus.ARCHIVED)
                    .toList();
        }
    }

    /**
     * 新增待办。
     */
    @Override
    public TodoItem add(String content, String priority, String dueAt) throws IOException {
        synchronized (lock) {
            if (content == null || content.isBlank()) {
                throw new IOException("todo content is required");
            }
            List<TodoItem> items = new ArrayList<>(readAll());
            TodoItem item = TodoItem.create(content.trim(), priority, dueAt);
            items.add(item);
            writeAll(items);
            return item;
        }
    }

    /**
     * 更新待办内容。
     */
    @Override
    public TodoItem update(String id, String content, String priority, String dueAt) throws IOException {
        synchronized (lock) {
            List<TodoItem> items = new ArrayList<>(readAll());
            int index = indexOf(items, id);
            TodoItem updated = items.get(index).updated(content, priority, dueAt);
            items.set(index, updated);
            writeAll(items);
            return updated;
        }
    }

    /**
     * 标记待办完成。
     */
    @Override
    public TodoItem complete(String id, String result) throws IOException {
        synchronized (lock) {
            List<TodoItem> items = new ArrayList<>(readAll());
            int index = indexOf(items, id);
            TodoItem completed = items.get(index).completed(result);
            items.set(index, completed);
            writeAll(items);
            return completed;
        }
    }

    /**
     * 归档待办。
     */
    @Override
    public TodoItem archive(String id) throws IOException {
        synchronized (lock) {
            List<TodoItem> items = new ArrayList<>(readAll());
            int index = indexOf(items, id);
            TodoItem archived = items.get(index).archived();
            items.set(index, archived);
            writeAll(items);
            return archived;
        }
    }

    /**
     * 从归档恢复待办。
     */
    @Override
    public TodoItem restore(String id) throws IOException {
        synchronized (lock) {
            List<TodoItem> items = new ArrayList<>(readAll());
            int index = indexOf(items, id);
            TodoItem restored = items.get(index).restored();
            items.set(index, restored);
            writeAll(items);
            return restored;
        }
    }

    /**
     * 从 todos.json 中物理删除待办。
     */
    @Override
    public TodoItem deletePermanently(String id) throws IOException {
        synchronized (lock) {
            List<TodoItem> items = new ArrayList<>(readAll());
            int index = indexOf(items, id);
            TodoItem deleted = items.remove(index);
            if (deleted.status() != TodoStatus.ARCHIVED) {
                throw new IOException("todo must be archived before physical delete: " + id);
            }
            writeAll(items);
            return deleted;
        }
    }

    /**
     * 找出 dueAt 已到期且仍 pending 的待办。
     */
    @Override
    public List<TodoItem> dueItems(Instant now) throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(item -> item.status() == TodoStatus.PENDING)
                    .filter(item -> dueAt(item).map(value -> !value.isAfter(now)).orElse(false))
                    .toList();
        }
    }

    /**
     * 查找待办。
     */
    @Override
    public Optional<TodoItem> find(String id) throws IOException {
        synchronized (lock) {
            return readAll().stream()
                    .filter(item -> Objects.equals(item.id(), id))
                    .findFirst();
        }
    }

    private List<TodoItem> readAll() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        TodoDocument document = objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), TodoDocument.class);
        return document.items();
    }

    private void writeAll(List<TodoItem> items) throws IOException {
        Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new TodoDocument(items)),
                StandardCharsets.UTF_8
        );
    }

    private int indexOf(List<TodoItem> items, String id) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i).id(), id)) {
                return i;
            }
        }
        throw new IOException("todo not found: " + id);
    }

    private Optional<Instant> dueAt(TodoItem item) {
        if (item.dueAt() == null || item.dueAt().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(item.dueAt()));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
