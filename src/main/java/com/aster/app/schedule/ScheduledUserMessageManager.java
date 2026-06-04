package com.aster.app.schedule;

import com.aster.app.schedule.model.ScheduleTrigger;
import com.aster.app.schedule.model.ScheduledUserMessage;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * 自动化用户消息统一入口。
 *
 * <p>工具层通过它创建、列出和取消 schedule；调度器到点后通过 dispatcher
 * 把 schedule 转成当前 session 的一次 user 输入。</p>
 */
public class ScheduledUserMessageManager implements AutoCloseable {
    private final ScheduledUserMessageStore store;
    private final String sessionId;
    private ScheduledUserMessageScheduler scheduler;

    public ScheduledUserMessageManager(ScheduledUserMessageStore store, String sessionId) {
        this.store = Objects.requireNonNull(store);
        this.sessionId = requireText(sessionId, "sessionId");
    }

    /**
     * 启动当前 session 的 schedule 调度器。
     */
    public synchronized void start(ScheduledUserMessageDispatcher dispatcher) {
        if (scheduler != null) {
            return;
        }
        scheduler = new ScheduledUserMessageScheduler(store, sessionId, dispatcher);
        scheduler.start();
    }

    /**
     * 创建指定时间执行一次的自动化用户消息。
     */
    public ScheduledUserMessage createOnce(String name, String content, String runAt) throws IOException {
        return create(name, content, ScheduleTrigger.once(requireText(runAt, "runAt")));
    }

    /**
     * 创建延迟执行一次的自动化用户消息。
     */
    public ScheduledUserMessage createDelay(String name, String content, long delaySeconds) throws IOException {
        return create(name, content, ScheduleTrigger.delay(delaySeconds));
    }

    /**
     * 创建固定间隔重复执行的自动化用户消息。
     */
    public ScheduledUserMessage createInterval(String name, String content, long intervalSeconds) throws IOException {
        if (intervalSeconds <= 0) {
            throw new IOException("intervalSeconds must be > 0");
        }
        return create(name, content, ScheduleTrigger.interval(intervalSeconds));
    }

    /**
     * 创建每日固定时间执行的自动化用户消息。
     */
    public ScheduledUserMessage createDaily(String name, String content, String dailyTime, String timezone) throws IOException {
        return create(name, content, ScheduleTrigger.daily(requireText(dailyTime, "dailyTime"), blankToNull(timezone)));
    }

    /**
     * 列出当前 session 的有效 schedule。
     */
    public List<ScheduledUserMessage> listActive() throws IOException {
        return store.listActive(sessionId);
    }

    /**
     * 取消当前 session 的 schedule。
     */
    public ScheduledUserMessage cancel(String scheduleId) throws IOException {
        ScheduledUserMessage schedule = store.find(requireText(scheduleId, "scheduleId"))
                .filter(item -> Objects.equals(item.sessionId(), sessionId))
                .orElseThrow(() -> new IOException("schedule not found: " + scheduleId));
        ScheduledUserMessage cancelled = store.update(schedule.cancelled());
        reschedule();
        return cancelled;
    }

    /**
     * 把 schedule 包装成提交给 Agent 的 user 输入。
     */
    public String renderUserInput(ScheduledUserMessage schedule) {
        String title = schedule.name() == null || schedule.name().isBlank()
                ? "定时任务"
                : schedule.name();
        return "定时任务触发：" + title + "\n\n" + schedule.content();
    }

    private ScheduledUserMessage create(String name, String content, ScheduleTrigger trigger) throws IOException {
        String cleanContent = requireText(content, "content");
        String nextRunAt = initialNextRunAt(trigger, Instant.now());
        ScheduledUserMessage created = store.create(ScheduledUserMessage.create(
                sessionId,
                name,
                cleanContent,
                trigger,
                nextRunAt
        ));
        reschedule();
        return created;
    }

    private void reschedule() {
        if (scheduler != null) {
            scheduler.reschedule();
        }
    }

    /**
     * 计算新建 schedule 的首次执行时间。
     */
    public static String initialNextRunAt(ScheduleTrigger trigger, Instant now) throws IOException {
        return switch (trigger.type()) {
            case "once" -> parseInstant(trigger.runAt()).toString();
            case "delay" -> now.plusSeconds(Math.max(0, trigger.delaySeconds())).toString();
            case "interval" -> now.plusSeconds(Math.max(1, trigger.intervalSeconds())).toString();
            case "daily" -> nextDailyRunAt(trigger.dailyTime(), trigger.timezone(), now).toString();
            default -> throw new IOException("unknown schedule trigger: " + trigger.type());
        };
    }

    /**
     * 计算 schedule 执行后的下一次时间；一次性 schedule 返回 null。
     */
    public static String nextRunAtAfter(ScheduleTrigger trigger, Instant now) throws IOException {
        return switch (trigger.type()) {
            case "once", "delay" -> null;
            case "interval" -> now.plusSeconds(Math.max(1, trigger.intervalSeconds())).toString();
            case "daily" -> nextDailyRunAt(trigger.dailyTime(), trigger.timezone(), now).toString();
            default -> throw new IOException("unknown schedule trigger: " + trigger.type());
        };
    }

    private static Instant nextDailyRunAt(String dailyTime, String timezone, Instant now) throws IOException {
        ZoneId zone = timezone == null || timezone.isBlank()
                ? ZoneId.systemDefault()
                : ZoneId.of(timezone.trim());
        LocalTime time = parseDailyTime(dailyTime);
        ZonedDateTime current = now.atZone(zone);
        ZonedDateTime candidate = current.toLocalDate().atTime(time).atZone(zone);
        if (!candidate.isAfter(current)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private static LocalTime parseDailyTime(String value) throws IOException {
        try {
            return LocalTime.parse(requireText(value, "dailyTime"));
        } catch (DateTimeParseException e) {
            throw new IOException("dailyTime must use HH:mm or HH:mm:ss", e);
        }
    }

    private static Instant parseInstant(String value) throws IOException {
        try {
            return Instant.parse(requireText(value, "runAt"));
        } catch (DateTimeParseException e) {
            throw new IOException("runAt must be ISO-8601 instant, for example 2026-06-05T04:00:00Z", e);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.close();
            scheduler = null;
        }
    }
}
