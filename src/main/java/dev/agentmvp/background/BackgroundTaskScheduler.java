package dev.agentmvp.background;

import dev.agentmvp.background.model.BackgroundTask;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 后台任务调度器。
 *
 * <p>教学版使用 ScheduledExecutorService。它足够表达 immediate、delay、interval，
 * 后续如果要 cron 或持久化下一次触发时间，再替换这一层。</p>
 */
public class BackgroundTaskScheduler implements AutoCloseable {
    private final ScheduledExecutorService scheduler;
    private final BackgroundTaskExecutor executor;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public BackgroundTaskScheduler(BackgroundTaskExecutor executor) {
        this(executor, Executors.newScheduledThreadPool(2));
    }

    public BackgroundTaskScheduler(BackgroundTaskExecutor executor, ScheduledExecutorService scheduler) {
        this.executor = Objects.requireNonNull(executor);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    /**
     * 根据 trigger 注册任务。
     */
    public void schedule(BackgroundTask task) {
        if (!task.enabled()) {
            return;
        }
        cancel(task.id());

        String type = task.trigger().type();
        ScheduledFuture<?> future = switch (type) {
            case "immediate" -> scheduler.schedule(() -> executor.execute(task), 0, TimeUnit.SECONDS);
            case "delay" -> scheduler.schedule(() -> executor.execute(task), positiveOrZero(task.trigger().delaySeconds()), TimeUnit.SECONDS);
            case "interval" -> scheduleInterval(task);
            default -> throw new IllegalArgumentException("Unsupported background task trigger: " + type);
        };
        futures.put(task.id(), future);
    }

    /**
     * 取消一个任务的调度。
     */
    public void cancel(String taskId) {
        ScheduledFuture<?> future = futures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private ScheduledFuture<?> scheduleInterval(BackgroundTask task) {
        long intervalSeconds = task.trigger().intervalSeconds();
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        long initialDelay = task.trigger().delaySeconds() > 0 ? task.trigger().delaySeconds() : intervalSeconds;
        return scheduler.scheduleAtFixedRate(
                () -> executor.execute(task),
                initialDelay,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    private long positiveOrZero(long value) {
        return Math.max(0, value);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        futures.clear();
    }
}
