package com.aster;

import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;
import com.aster.core.event.model.AgentEventMeta;
import com.aster.app.background.model.BackgroundTask;
import com.aster.app.background.model.TaskAction;
import com.aster.app.background.model.TaskRun;
import com.aster.app.background.model.TaskTrigger;
import com.aster.ui.im.telegram.TelegramAgentEventHandler;
import com.aster.ui.im.telegram.TelegramMessageSender;
import com.aster.ui.im.telegram.TelegramNotificationSink;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telegram Agent 事件回传测试。
 */
class TelegramAgentEventHandlerTest {
    /**
     * 验证流式 token 会合并成最终消息，RunFinished 和 Done 不重复发送。
     */
    @Test
    void sendsFinalAnswerOnlyOnce() {
        FakeSender sender = new FakeSender();
        TelegramAgentEventHandler handler = new TelegramAgentEventHandler(sender, 123L);

        handler.onEvent(envelope(new AgentEvent.RunStarted("你好")));
        handler.onEvent(envelope(new AgentEvent.AssistantToken("你")));
        handler.onEvent(envelope(new AgentEvent.AssistantToken("好")));
        handler.onEvent(envelope(new AgentEvent.RunFinished("你好")));
        handler.onEvent(envelope(new AgentEvent.Done("你好")));

        assertEquals(List.of("typing"), sender.actions);
        assertEquals(List.of("你好"), sender.messages);
    }

    /**
     * 验证失败工具结果会被预览并截断。
     */
    @Test
    void sendsTruncatedFailedToolPreview() {
        FakeSender sender = new FakeSender();
        TelegramAgentEventHandler handler = new TelegramAgentEventHandler(sender, 123L);

        handler.onEvent(envelope(new AgentEvent.ToolCallDone(
                "call_1",
                "read",
                "x".repeat(700),
                false,
                3
        )));

        assertEquals(1, sender.messages.size());
        assertTrue(sender.messages.getFirst().contains("工具调用失败：read"));
        assertTrue(sender.messages.getFirst().contains("已截断"));
    }

    /**
     * 验证 bash 成功结果也会发到 Telegram，方便用户看到命令输出。
     */
    @Test
    void sendsSuccessfulBashPreview() {
        FakeSender sender = new FakeSender();
        TelegramAgentEventHandler handler = new TelegramAgentEventHandler(sender, 123L);

        handler.onEvent(envelope(new AgentEvent.ToolCallDone(
                "call_1",
                "bash",
                "exitCode=0\ncommand=echo hello\n\nhello",
                true,
                12
        )));

        assertEquals(1, sender.messages.size());
        assertTrue(sender.messages.getFirst().contains("工具调用完成：bash"));
        assertTrue(sender.messages.getFirst().contains("hello"));
    }

    /**
     * 验证后台任务通知会把执行结果正文发给 Telegram。
     */
    @Test
    void sendsBackgroundTaskMessage() {
        FakeSender sender = new FakeSender();
        TelegramNotificationSink sink = new TelegramNotificationSink(sender, 123L);
        BackgroundTask task = BackgroundTask.create(
                "测试提醒",
                TaskTrigger.immediate(),
                new TaskAction("reminder", java.util.Map.of())
        );

        sink.backgroundTaskCompleted(TaskRun.success(
                task,
                "2026-06-04T00:00:00Z",
                "2026-06-04T00:00:01Z",
                "该看结果了"
        ));

        assertEquals(List.of("后台任务完成：测试提醒\n该看结果了"), sender.messages);
    }

    private AgentEventEnvelope envelope(AgentEvent event) {
        return new AgentEventEnvelope(
                new AgentEventMeta("event", "run", "session", 1, Instant.parse("2026-06-04T00:00:00Z")),
                event
        );
    }

    private static final class FakeSender implements TelegramMessageSender {
        private final List<String> messages = new ArrayList<>();
        private final List<String> actions = new ArrayList<>();

        @Override
        public void sendMessage(long chatId, String text) throws IOException {
            messages.add(text);
        }

        @Override
        public void sendChatAction(long chatId, String action) throws IOException {
            actions.add(action);
        }
    }
}
