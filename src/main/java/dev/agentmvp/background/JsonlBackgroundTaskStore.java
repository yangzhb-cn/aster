package dev.agentmvp.background;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.background.model.BackgroundTask;
import dev.agentmvp.background.model.TaskRun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSONL 后台任务存储。
 *
 * <p>tasks.jsonl 采用 append-only 方式保存任务定义的每次版本。
 * 读取时按 id 合并，最后一条就是当前状态。runs.jsonl 只追加执行记录。</p>
 */
public class JsonlBackgroundTaskStore implements BackgroundTaskStore {
    private final ObjectMapper objectMapper;
    private final Path tasksFile;
    private final Path runsFile;

    public JsonlBackgroundTaskStore(ObjectMapper objectMapper, Path tasksFile, Path runsFile) throws IOException {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.tasksFile = Objects.requireNonNull(tasksFile);
        this.runsFile = Objects.requireNonNull(runsFile);
        Files.createDirectories(tasksFile.getParent());
        Files.createDirectories(runsFile.getParent());
    }

    @Override
    public synchronized void saveTask(BackgroundTask task) throws IOException {
        appendJson(tasksFile, task);
    }

    @Override
    public synchronized List<BackgroundTask> listTasks() throws IOException {
        Map<String, BackgroundTask> latest = new LinkedHashMap<>();
        for (BackgroundTask task : readJsonl(tasksFile, BackgroundTask.class)) {
            latest.put(task.id(), task);
        }
        return new ArrayList<>(latest.values());
    }

    @Override
    public synchronized Optional<BackgroundTask> findTask(String taskId) throws IOException {
        return listTasks().stream()
                .filter(task -> Objects.equals(task.id(), taskId))
                .findFirst();
    }

    @Override
    public synchronized void appendRun(TaskRun run) throws IOException {
        appendJson(runsFile, run);
    }

    @Override
    public synchronized List<TaskRun> listRuns() throws IOException {
        return readJsonl(runsFile, TaskRun.class);
    }

    private void appendJson(Path file, Object value) throws IOException {
        Files.writeString(
                file,
                objectMapper.writeValueAsString(value) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private <T> List<T> readJsonl(Path file, Class<T> type) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }

        List<T> values = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                values.add(objectMapper.readValue(line, type));
            }
        }
        return values;
    }
}
