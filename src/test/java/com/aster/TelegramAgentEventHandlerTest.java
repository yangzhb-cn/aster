package com.aster;

import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;
import com.aster.core.event.model.AgentEventMeta;
import com.aster.ui.im.telegram.TelegramAgentEventHandler;
import com.aster.ui.im.telegram.TelegramMessageSender;
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
