package com.aster.app.schedule;

import com.aster.app.schedule.model.ScheduledUserMessage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 自动化用户消息存储。
 */
public interface ScheduledUserMessageStore {
    /**
     * 列出全部 schedule。
     */
    List<ScheduledUserMessage> listAll() throws IOException;

    /**
     * 列出指定 session 下仍启用的 schedule。
     */
    List<ScheduledUserMessage> listActive(String sessionId) throws IOException;

    /**
     * 创建 schedule。
     */
    ScheduledUserMessage create(ScheduledUserMessage schedule) throws IOException;

    /**
     * 更新 schedule。
     */
    ScheduledUserMessage update(ScheduledUserMessage schedule) throws IOException;

    /**
     * 查找 schedule。
     */
    Optional<ScheduledUserMessage> find(String scheduleId) throws IOException;
}
