package com.aster.app.schedule;

import com.aster.app.schedule.model.ScheduledUserMessage;

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
 * 自动化用户消息精准调度器。
 *
 * <p>它不做固定间隔轮询，而是每次计算当前 session 最近的 nextRunAt，
 * 只在那一刻唤醒。新增、取消或执行完成后重新计算下一次唤醒时间。</p>
 */
public class ScheduledUserMessageScheduler implements AutoCloseable {
    private final ScheduledUserMessageStore store;
    private final String sessionId;
    private final ScheduledUserMessageDispatcher dispatcher;
    private final ScheduledExecutorService executor;
    private final HashSet<String> runningIds = new HashSet<>();

    private ScheduledFuture<?> wakeFuture;
    private boolean started;

    public ScheduledUserMessageScheduler(
            ScheduledUserMessageStore store,
            String sessionId,
            ScheduledUserMessageDispatcher dispatcher
    ) {
        this(store, sessionId, dispatcher, Executors.newScheduledThreadPool(2));
    }

    public ScheduledUserMessageScheduler(
            ScheduledUserMessageStore store,
            String sessionId,
            ScheduledUserMessageDispatcher dispatcher,
            ScheduledExecutorService executor
    ) {
        this.store = Objects.requireNonNull(store);
        this.sessionId = requireText(sessionId, "sessionId");
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * 启动调度器。
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        reschedule();
    }

    /**
     * 任务清单变化后，重新计算最近一次唤醒时间。
     */
    public void reschedule() {
        executor.execute(this::scheduleNextSafely);
    }

    private void scheduleNextSafely() {
        try {
            scheduleNext();
        } catch (IOException | RuntimeException ignored) {
            // 调度失败不能终止后续任务变化触发的重新调度。
        }
    }

    private synchronized void scheduleNext() throws IOException {
        if (!started) {
            return;
        }
        if (wakeFuture != null) {
            wakeFuture.cancel(false);
            wakeFuture = null;
        }

        ScheduledUserMessage next = nextSchedule();
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(0, parseInstant(next.nextRunAt()).toEpochMilli() - Instant.now().toEpochMilli());
        wakeFuture = executor.schedule(this::runDueSafely, delayMillis, TimeUnit.MILLISECONDS);
    }

    private ScheduledUserMessage nextSchedule() throws IOException {
        return store.listActive(sessionId).stream()
                .filter(schedule -> schedule.nextRunAt() != null && !schedule.nextRunAt().isBlank())
                .min(Comparator.comparing(schedule -> parseInstant(schedule.nextRunAt())))
                .orElse(null);
    }

    private void runDueSafely() {
        try {
            runDue();
        } catch (IOException | RuntimeException ignored) {
            // 单次唤醒失败后继续重新计算下一次时间。
        } finally {
            reschedule();
        }
    }

    private void runDue() throws IOException {
        Instant now = Instant.now();
        List<ScheduledUserMessage> dueSchedules = store.listActive(sessionId).stream()
                .filter(schedule -> !parseInstant(schedule.nextRunAt()).isAfter(now))
                .toList();
        for (ScheduledUserMessage schedule : dueSchedules) {
            if (markRunning(schedule.id())) {
                executor.execute(() -> dispatchAndUpdate(schedule));
            }
        }
    }

    private void dispatchAndUpdate(ScheduledUserMessage schedule) {
        try {
            dispatcher.dispatch(schedule);
            updateAfterDispatch(schedule);
        } catch (Exception e) {
            markFailed(schedule);
        } finally {
            synchronized (runningIds) {
                runningIds.remove(schedule.id());
            }
            reschedule();
        }
    }

    private void updateAfterDispatch(ScheduledUserMessage schedule) throws IOException {
        String finishedAt = Instant.now().toString();
        String nextRunAt = ScheduledUserMessageManager.nextRunAtAfter(schedule.trigger(), Instant.now());
        if (nextRunAt == null || nextRunAt.isBlank()) {
            store.update(schedule.completed(finishedAt));
        } else {
            store.update(schedule.rescheduled(finishedAt, nextRunAt));
        }
    }

    private void markFailed(ScheduledUserMessage schedule) {
        try {
            store.update(schedule.failed(Instant.now().toString()));
        } catch (IOException ignored) {
            // 失败状态写入失败时只能等待下一次外部清单变化重新调度。
        }
    }

    private boolean markRunning(String scheduleId) {
        synchronized (runningIds) {
            return runningIds.add(scheduleId);
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    @Override
    public synchronized void close() {
        started = false;
        if (wakeFuture != null) {
            wakeFuture.cancel(true);
            wakeFuture = null;
        }
        executor.shutdownNow();
        synchronized (runningIds) {
            runningIds.clear();
        }
    }
}
