package dev.agentmvp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentmvp.background.BackgroundTaskEventBus;
import dev.agentmvp.background.BackgroundTaskExecutor;
import dev.agentmvp.background.BackgroundTaskManager;
import dev.agentmvp.background.BackgroundTaskScheduler;
import dev.agentmvp.background.JsonlBackgroundTaskStore;
import dev.agentmvp.background.NoopTaskHandler;
import dev.agentmvp.background.model.BackgroundTask;
import dev.agentmvp.background.model.BackgroundTaskEvent;
import dev.agentmvp.background.model.TaskAction;
import dev.agentmvp.background.model.TaskRun;
import dev.agentmvp.background.model.TaskRunStatus;
import dev.agentmvp.background.model.TaskStatus;
import dev.agentmvp.background.model.TaskTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
     * 验证 immediate 任务会执行 noop handler，并写入 runs.jsonl。
     */
    @Test
    void executesImmediateNoopTaskAndPublishesEvent() throws Exception {
        JsonlBackgroundTaskStore store = store();
        List<BackgroundTaskEvent> events = new ArrayList<>();
        BackgroundTaskManager manager = manager(store, events);

        try {
            BackgroundTask task = BackgroundTask.create(
                    "测试立即任务",
                    TaskTrigger.immediate(),
                    TaskAction.noop()
            );

            manager.create(task);

            List<TaskRun> runs = waitForRuns(store, 1);
            waitForEvents(events, 1);
            assertEquals(TaskRunStatus.SUCCESS, runs.get(0).status());
            assertEquals("noop task completed", runs.get(0).message());
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
                    TaskAction.noop()
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
                List.of(new NoopTaskHandler()),
                eventBus
        );
        return new BackgroundTaskManager(
                store,
                new BackgroundTaskScheduler(executor)
        );
    }

    private List<TaskRun> waitForRuns(JsonlBackgroundTaskStore store, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
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
