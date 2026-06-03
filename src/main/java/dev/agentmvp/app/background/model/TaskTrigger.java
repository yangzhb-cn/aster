package dev.agentmvp.app.background.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 后台任务触发方式。
 *
 * <p>教学版先支持三种触发：immediate 立即执行一次、delay 延迟执行一次、
 * interval 按固定间隔重复执行。cron、事件触发和复杂重试先不放进 MVP。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TaskTrigger(
        String type,
        long delaySeconds,
        long intervalSeconds,
        String eventType
) {
    /**
     * 创建立即执行一次的触发器。
     */
    public static TaskTrigger immediate() {
        return new TaskTrigger("immediate", 0, 0, null);
    }

    /**
     * 创建延迟执行一次的触发器。
     */
    public static TaskTrigger delay(long delaySeconds) {
        return new TaskTrigger("delay", delaySeconds, 0, null);
    }

    /**
     * 创建固定间隔重复执行的触发器。
     */
    public static TaskTrigger interval(long intervalSeconds) {
        return new TaskTrigger("interval", 0, intervalSeconds, null);
    }
}
