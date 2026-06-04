package com.aster;

import com.aster.app.schedule.JsonScheduledUserMessageStore;
import com.aster.app.schedule.ScheduledUserMessageManager;
import com.aster.app.schedule.ScheduledUserMessageStore;
import com.aster.app.schedule.model.ScheduleStatus;
import com.aster.app.schedule.model.ScheduledUserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledUserMessageTest {
    @TempDir
    Path tempDir;

    @Test
    void delayScheduleDispatchesUserMessage() throws Exception {
        ScheduledUserMessageStore store = new JsonScheduledUserMessageStore(new ObjectMapper(), tempDir.resolve("schedules.json"));
        ScheduledUserMessageManager manager = new ScheduledUserMessageManager(store, "session_a");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> submitted = new AtomicReference<>();

        manager.start(schedule -> {
            submitted.set(manager.renderUserInput(schedule));
            latch.countDown();
        });

        ScheduledUserMessage schedule = manager.createDelay("提醒", "告诉我该休息了", 0);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(submitted.get().contains("定时任务触发：提醒"));
        assertTrue(submitted.get().contains("告诉我该休息了"));
        for (int i = 0; i < 20 && store.find(schedule.id()).orElseThrow().enabled(); i++) {
            Thread.sleep(25);
        }
        assertFalse(store.find(schedule.id()).orElseThrow().enabled());
        manager.close();
    }

    @Test
    void dailyScheduleComputesFutureNextRunAt() throws Exception {
        ScheduledUserMessageStore store = new JsonScheduledUserMessageStore(new ObjectMapper(), tempDir.resolve("daily-schedules.json"));
        ScheduledUserMessageManager manager = new ScheduledUserMessageManager(store, "session_a");

        ScheduledUserMessage schedule = manager.createDaily("每日新闻", "总结今天 AI 新闻", "12:00", "Asia/Shanghai");

        assertEquals("daily", schedule.trigger().type());
        assertTrue(Instant.parse(schedule.nextRunAt()).isAfter(Instant.now()));
    }

    @Test
    void failedDispatchDisablesSchedule() throws Exception {
        ScheduledUserMessageStore store = new JsonScheduledUserMessageStore(new ObjectMapper(), tempDir.resolve("failed-schedules.json"));
        ScheduledUserMessageManager manager = new ScheduledUserMessageManager(store, "session_a");

        manager.start(schedule -> {
            throw new IllegalStateException("submit failed");
        });

        ScheduledUserMessage schedule = manager.createDelay("失败测试", "这条不应反复触发", 0);

        for (int i = 0; i < 20 && store.find(schedule.id()).orElseThrow().status() == ScheduleStatus.ACTIVE; i++) {
            Thread.sleep(25);
        }
        ScheduledUserMessage updated = store.find(schedule.id()).orElseThrow();
        assertEquals(ScheduleStatus.FAILED, updated.status());
        assertFalse(updated.enabled());
        manager.close();
    }
}
