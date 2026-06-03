package com.aster.ui.web;

import com.aster.app.background.model.TaskRun;
import com.aster.app.notification.NotificationSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;

/**
 * Web 后台任务通知出口。
 *
 * <p>后台任务仍然只依赖 NotificationSink；这里把完成或失败通知转成 SSE，
 * 让浏览器右侧状态区可以观察后台任务结果。</p>
 */
public class WebNotificationSink implements NotificationSink {
    private final ObjectMapper objectMapper;
    private final WebSseClientRegistry clients;

    public WebNotificationSink(ObjectMapper objectMapper, WebSseClientRegistry clients) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clients = Objects.requireNonNull(clients);
    }

    @Override
    public void backgroundTaskCompleted(TaskRun run) {
        broadcast("BackgroundTaskCompleted", run);
    }

    @Override
    public void backgroundTaskFailed(TaskRun run) {
        broadcast("BackgroundTaskFailed", run);
    }

    private void broadcast(String type, TaskRun run) {
        try {
            clients.broadcast("notification", objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "run", run
            )));
        } catch (JsonProcessingException e) {
            clients.broadcast("notification", "{\"type\":\"WebNotificationSerializationFailed\"}");
        }
    }
}
