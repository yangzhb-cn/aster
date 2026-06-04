package com.aster.app.background;

import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskRun;
import com.aster.app.background.model.TaskStatus;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 后台任务扫描调度器。
 *
 * <p>它每隔固定秒数读取任务清单和运行记录，判断 immediate、delay、interval
 * 哪些任务已经到期。调度状态来自 JSONL 清单，避免重启后丢失内存 future。</p>
 */
public class BackgroundTaskScheduler implements AutoCloseable {
    private static final long DEFAULT_SCAN_INTERVAL_SECONDS = 10;

    private final BackgroundTaskStore store;
    private final BackgroundTaskExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final long scanIntervalSeconds;
    private final HashSet<String> runningTaskIds = new HashSet<>();
    private ScheduledFuture<?> scanFuture;

    public BackgroundTaskScheduler(BackgroundTaskStore store, BackgroundTaskExecutor executor) {
        this(store, executor, Executors.newScheduledThreadPool(2), configuredScanIntervalSeconds());
    }

    public BackgroundTaskScheduler(
            BackgroundTaskStore store,
            BackgroundTaskExecutor executor,
            ScheduledExecutorService scheduler,
            long scanIntervalSeconds
    ) {
        this.store = Objects.requireNonNull(store);
        this.executor = Objects.requireNonNull(executor);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.scanIntervalSeconds = Math.max(1, scanIntervalSeconds);
    }

    /**
     * 启动固定间隔扫描。
     */
    public synchronized void start() {
        if (scanFuture != null && !scanFuture.isCancelled()) {
            return;
        }
        scanFuture = scheduler.scheduleWithFixedDelay(
                this::scanSafely,
                0,
                scanIntervalSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * 立即触发一次扫描，供新任务创建后尽快执行 immediate 任务。
     */
    public void scanNow() {
        scheduler.execute(this::scanSafely);
    }

    /**
     * 清理指定任务的运行中标记。
     *
     * <p>任务取消状态由 BackgroundTaskManager 先写入 store；
     * 下一轮扫描会自然跳过 disabled 任务。</p>
     */
    public void cancel(String taskId) {
        synchronized (runningTaskIds) {
            runningTaskIds.remove(taskId);
        }
    }

    private void scanSafely() {
        try {
            scanDueTasks();
        } catch (IOException | RuntimeException ignored) {
            // 扫描失败不能终止后续周期。
        }
    }

    private void scanDueTasks() throws IOException {
        Instant now = Instant.now();
        List<BackgroundTask> tasks = store.listTasks();
        List<TaskRun> runs = store.listRuns();
        for (BackgroundTask task : tasks) {
            if (isDue(task, runs, now) && markRunning(task.id())) {
                scheduler.execute(() -> executeAndRelease(task));
            }
        }
    }

    private boolean isDue(BackgroundTask task, List<TaskRun> runs, Instant now) {
        if (!task.enabled() || task.status() != TaskStatus.ACTIVE || task.trigger() == null) {
            return false;
        }
        return switch (task.trigger().type()) {
            case "immediate" -> true;
            case "delay" -> isDelayDue(task, now);
            case "interval" -> isIntervalDue(task, runs, now);
            default -> false;
        };
    }

    private boolean isDelayDue(BackgroundTask task, Instant now) {
        Instant createdAt = parseInstant(task.createdAt());
        long delaySeconds = Math.max(0, task.trigger().delaySeconds());
        return !createdAt.plusSeconds(delaySeconds).isAfter(now);
    }

    private boolean isIntervalDue(BackgroundTask task, List<TaskRun> runs, Instant now) {
        long intervalSeconds = task.trigger().intervalSeconds();
        if (intervalSeconds <= 0) {
            return false;
        }
        TaskRun latestRun = latestRun(task.id(), runs);
        if (latestRun == null) {
            long initialDelay = task.trigger().delaySeconds() > 0
                    ? task.trigger().delaySeconds()
                    : intervalSeconds;
            return !parseInstant(task.createdAt()).plusSeconds(initialDelay).isAfter(now);
        }
        return !parseInstant(latestRun.finishedAt()).plusSeconds(intervalSeconds).isAfter(now);
    }

    private TaskRun latestRun(String taskId, List<TaskRun> runs) {
        return runs.stream()
                .filter(run -> Objects.equals(taskId, run.taskId()))
                .max(Comparator.comparing(run -> parseInstant(run.finishedAt())))
                .orElse(null);
    }

    private boolean markRunning(String taskId) {
        synchronized (runningTaskIds) {
            return runningTaskIds.add(taskId);
        }
    }

    private void executeAndRelease(BackgroundTask task) {
        try {
            executor.execute(task);
        } finally {
            synchronized (runningTaskIds) {
                runningTaskIds.remove(task.id());
            }
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return Instant.EPOCH;
        }
    }

    private static long configuredScanIntervalSeconds() {
        String value = System.getenv("BACKGROUND_TASK_SCAN_INTERVAL_SECONDS");
        if (value == null || value.isBlank()) {
            value = System.getenv("SCHEDULE_INTERVAL_SECONDS");
        }
        if (value == null || value.isBlank()) {
            return DEFAULT_SCAN_INTERVAL_SECONDS;
        }
        try {
            return Math.max(1, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_SCAN_INTERVAL_SECONDS;
        }
    }

    @Override
    public void close() {
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
        scheduler.shutdownNow();
        synchronized (runningTaskIds) {
            runningTaskIds.clear();
        }
    }
}
