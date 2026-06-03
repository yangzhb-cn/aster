package com.aster.ui.im.telegram;

import com.aster.core.event.AgentEventHandler;
import com.aster.core.event.model.AgentEvent;
import com.aster.core.event.model.AgentEventEnvelope;

import java.io.IOException;
import java.util.Objects;

/**
 * 把 AgentEvent 转成 Telegram 消息。
 *
 * <p>Telegram 不适合逐 token 刷屏，所以 assistant token 只缓存在内存里，
 * 等 Done/RunFinished 到来后一次性发送最终回答。</p>
 */
public class TelegramAgentEventHandler implements AgentEventHandler {
    private static final int TOOL_PREVIEW_LIMIT = 600;

    private final TelegramMessageSender sender;
    private final long chatId;
    private final StringBuilder assistantBuffer = new StringBuilder();
    private boolean finalSent;

    public TelegramAgentEventHandler(TelegramMessageSender sender, long chatId) {
        this.sender = Objects.requireNonNull(sender);
        this.chatId = chatId;
    }

    /**
     * 处理 Agent 事件并发送必要的 Telegram 消息。
     */
    @Override
    public synchronized void onEvent(AgentEventEnvelope envelope) {
        try {
            handle(envelope.event());
        } catch (IOException e) {
            System.err.println("Telegram send failed: " + e.getMessage());
        }
    }

    private void handle(AgentEvent event) throws IOException {
        if (event instanceof AgentEvent.RunStarted) {
            assistantBuffer.setLength(0);
            finalSent = false;
            sender.sendChatAction(chatId, "typing");
            return;
        }
        if (event instanceof AgentEvent.AssistantToken token) {
            assistantBuffer.append(token.text());
            return;
        }
        if (event instanceof AgentEvent.RunQueued queued) {
            sender.sendMessage(chatId, "已进入队列，当前队列：" + queued.queueSize());
            return;
        }
        if (event instanceof AgentEvent.ToolCallStart tool) {
            sender.sendMessage(chatId, "工具调用开始：" + tool.toolName());
            return;
        }
        if (event instanceof AgentEvent.ToolCallDone tool) {
            String status = tool.success() ? "完成" : "失败";
            String preview = tool.success() ? "" : "\n" + preview(tool.text());
            sender.sendMessage(chatId, "工具调用" + status + "：" + tool.toolName() + " · " + tool.elapsedMillis() + "ms" + preview);
            return;
        }
        if (event instanceof AgentEvent.RunFailed failed) {
            finalSent = true;
            sender.sendMessage(chatId, "执行失败：" + failed.errorMessage());
            return;
        }
        if (event instanceof AgentEvent.RunStopped stopped) {
            sendFinal("已停止。\n" + stopped.finalText());
            return;
        }
        if (event instanceof AgentEvent.RunFinished finished) {
            sendFinal(finished.finalText());
            return;
        }
        if (event instanceof AgentEvent.Done done) {
            sendFinal(done.finalText());
        }
    }

    private void sendFinal(String finalText) throws IOException {
        if (finalSent) {
            return;
        }
        finalSent = true;
        String text = finalText == null || finalText.isBlank()
                ? assistantBuffer.toString()
                : finalText;
        if (!text.isBlank()) {
            sender.sendMessage(chatId, text);
        }
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= TOOL_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, TOOL_PREVIEW_LIMIT) + "\n... 已截断 " + text.length() + " 字符";
    }
}
