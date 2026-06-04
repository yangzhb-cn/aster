package com.aster.app.schedule.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 自动化用户消息触发规则。
 *
 * <p>schedule 面向“到点给 Agent 发一条 user 消息”，所以支持一次性、
 * 延迟、固定间隔和每日固定时间四种规则。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScheduleTrigger(
        String type,
        String runAt,
        long delaySeconds,
        long intervalSeconds,
        String dailyTime,
        String timezone
) {
    /**
     * 创建指定时间执行一次的触发器。
     */
    public static ScheduleTrigger once(String runAt) {
        return new ScheduleTrigger("once", runAt, 0, 0, null, null);
    }

    /**
     * 创建延迟执行一次的触发器。
     */
    public static ScheduleTrigger delay(long delaySeconds) {
        return new ScheduleTrigger("delay", null, Math.max(0, delaySeconds), 0, null, null);
    }

    /**
     * 创建固定间隔重复执行的触发器。
     */
    public static ScheduleTrigger interval(long intervalSeconds) {
        return new ScheduleTrigger("interval", null, 0, Math.max(1, intervalSeconds), null, null);
    }

    /**
     * 创建每日固定时间执行的触发器。
     */
    public static ScheduleTrigger daily(String dailyTime, String timezone) {
        return new ScheduleTrigger("daily", null, 0, 0, dailyTime, timezone);
    }
}
