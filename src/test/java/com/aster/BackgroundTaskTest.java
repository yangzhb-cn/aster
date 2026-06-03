package com.aster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aster.app.background.BackgroundTaskEventBus;
import com.aster.app.background.BackgroundTaskExecutor;
import com.aster.app.background.BackgroundTaskManager;
import com.aster.app.background.BackgroundTaskScheduler;
import com.aster.app.background.JsonlBackgroundTaskStore;
import com.aster.app.background.ReminderTaskHandler;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.BackgroundTaskEvent;
import com.aster.app.background.model.TaskAction;
import com.aster.app.background.model.TaskRun;
import com.aster.app.background.model.TaskRunStatus;
import com.aster.app.background.model.TaskStatus;
import com.aster.app.background.model.TaskTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台任务框架测试。
 *
 * <p>这里不测试具体业务任务，只验证任务定义持久化、调度、执行记录和事件通知链路。</p>
 */
class BackgroundTaskTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 验证 immediate 任务会执行 reminder handler，并写入 runs.jsonl。
     */
    @Test
    void executesImmediateReminderTaskAndPublishesEvent() throws Exception {
        JsonlBackgroundTaskStore store = store();
        List<BackgroundTaskEvent> events = new ArrayList<>();
        BackgroundTaskManager manager = manager(store, events);

        try {
            BackgroundTask task = BackgroundTask.create(
                    "测试立即任务",
                    TaskTrigger.immediate(),
                    new TaskAction("reminder", Map.of("text", "立即提醒"))
            );

            manager.create(task);

            List<TaskRun> runs = waitForRuns(store, 1);
            waitForEvents(events, 1);
            assertEquals(TaskRunStatus.SUCCESS, runs.get(0).status());
            assertEquals("立即提醒", runs.get(0).message());
            assertTrue(events.get(0) instanceof BackgroundTaskEvent.TaskCompleted);
            assertEquals(TaskStatus.COMPLETED, store.findTask(task.id()).orElseThrow().status());
        } finally {
            manager.close();
        }
    }

    /**
     * 验证取消 delay 任务后，调度器不会再执行它。
     */
    @Test
    void cancelsDelayTaskBeforeItRuns() throws Exception {
        JsonlBackgroundTaskStore store = store();
        List<BackgroundTaskEvent> events = new ArrayList<>();
        BackgroundTaskManager manager = manager(store, events);

        try {
            BackgroundTask task = BackgroundTask.create(
                    "测试取消任务",
                    TaskTrigger.delay(2),
                    new TaskAction("reminder", Map.of("text", "不应该执行"))
            );

            manager.create(task);
            manager.cancel(task.id());

            TimeUnit.MILLISECONDS.sleep(300);

            assertEquals(List.of(), store.listRuns());
            assertEquals(TaskStatus.CANCELLED, store.findTask(task.id()).orElseThrow().status());
            assertEquals(List.of(), events);
        } finally {
            manager.close();
        }
    }

    /**
     * 验证扫描器会周期性读取任务清单并重复执行 interval 任务。
     */
    @Test
    void scannerRunsIntervalTaskFromTaskList() throws Exception {
        JsonlBackgroundTaskStore store = store();
        List<BackgroundTaskEvent> events = new ArrayList<>();
        BackgroundTaskManager manager = manager(store, events);

        try {
            BackgroundTask task = BackgroundTask.create(
                    "测试周期提醒",
                    TaskTrigger.interval(1),
                    new TaskAction("reminder", Map.of("text", "周期提醒"))
            );

            manager.create(task);
            manager.start();

            List<TaskRun> runs = waitForRuns(store, 2, 3);
            assertTrue(runs.size() >= 2);
            assertEquals("周期提醒", runs.get(0).message());
            assertEquals(TaskStatus.ACTIVE, store.findTask(task.id()).orElseThrow().status());
        } finally {
            manager.close();
        }
    }

    private JsonlBackgroundTaskStore store() throws Exception {
        return new JsonlBackgroundTaskStore(
                objectMapper,
                tempDir.resolve("tasks.jsonl"),
                tempDir.resolve("runs.jsonl")
        );
    }

    private BackgroundTaskManager manager(JsonlBackgroundTaskStore store, List<BackgroundTaskEvent> events) {
        BackgroundTaskEventBus eventBus = new BackgroundTaskEventBus(List.of(events::add));
        BackgroundTaskExecutor executor = new BackgroundTaskExecutor(
                store,
                List.of(new ReminderTaskHandler()),
                eventBus
        );
        return new BackgroundTaskManager(
                store,
                new BackgroundTaskScheduler(store, executor, Executors.newScheduledThreadPool(2), 1)
        );
    }

    private List<TaskRun> waitForRuns(JsonlBackgroundTaskStore store, int expected) throws Exception {
        return waitForRuns(store, expected, 2);
    }

    private List<TaskRun> waitForRuns(JsonlBackgroundTaskStore store, int expected, int timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            List<TaskRun> runs = store.listRuns();
            if (runs.size() >= expected) {
                return runs;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return store.listRuns();
    }

    private void waitForEvents(List<BackgroundTaskEvent> events, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (events.size() >= expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }
}
