package com.aster.app.schedule;

import com.aster.app.schedule.model.ScheduledUserMessage;

/**
 * 自动化用户消息派发器。
 */
@FunctionalInterface
public interface ScheduledUserMessageDispatcher {
    /**
     * 到点后把 schedule 转成一次 user 输入。
     */
    void dispatch(ScheduledUserMessage schedule) throws Exception;
}
