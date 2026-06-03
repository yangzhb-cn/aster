package com.aster.ui.im.telegram;

import com.aster.ui.im.telegram.model.TelegramMessage;
import com.aster.ui.im.telegram.model.TelegramUpdate;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/**
 * Telegram long polling 循环。
 *
 * <p>它只负责拉取消息、做 chatId 白名单过滤和命令分发；
 * 真正的 Agent 执行交给 TelegramRuntimeManager。</p>
 */
public class TelegramUpdatePoller implements AutoCloseable {
    private final TelegramBotClient botClient;
    private final TelegramRuntimeManager runtimeManager;
    private final Set<Long> allowedChatIds;
    private volatile boolean closed;
    private long offset;

    public TelegramUpdatePoller(
            TelegramBotClient botClient,
            TelegramRuntimeManager runtimeManager,
            Set<Long> allowedChatIds
    ) {
        this.botClient = Objects.requireNonNull(botClient);
        this.runtimeManager = Objects.requireNonNull(runtimeManager);
        this.allowedChatIds = Set.copyOf(Objects.requireNonNull(allowedChatIds));
        if (this.allowedChatIds.isEmpty()) {
            throw new IllegalArgumentException("TELEGRAM_ALLOWED_CHAT_IDS is required");
        }
    }

    /**
     * 阻塞运行 Telegram long polling。
     */
    public void runForever() {
        while (!closed) {
            try {
                pollOnce();
            } catch (Exception e) {
                System.err.println("Telegram polling failed: " + e.getMessage());
                sleepQuietly();
            }
        }
    }

    /**
     * 执行一次 getUpdates 拉取，测试和主循环共用。
     */
    public void pollOnce() throws IOException {
        for (TelegramUpdate update : botClient.getUpdates(offset, 30)) {
            offset = Math.max(offset, update.updateId() + 1);
            handleUpdate(update);
        }
    }

    private void handleUpdate(TelegramUpdate update) throws IOException {
        TelegramMessage message = update.message();
        if (message == null || message.chat() == null || message.text() == null || message.text().isBlank()) {
            return;
        }

        long chatId = message.chat().id();
        if (!allowedChatIds.contains(chatId)) {
            return;
        }

        String text = message.text().trim();
        if (text.startsWith("/")) {
            handleCommand(message, text);
            return;
        }
        runtimeManager.submit(message, text);
    }

    private void handleCommand(TelegramMessage message, String text) throws IOException {
        String command = normalizeCommand(text);
        switch (command) {
            case "/start", "/help" -> botClient.sendMessage(message.chat().id(), """
                    Aster Telegram 已连接。

                    直接发送文本即可对话。
                    /session 查看当前会话
                    /new 新建会话
                    /stop 停止当前任务
                    """.stripTrailing());
            case "/session" -> runtimeManager.showSession(message);
            case "/new" -> runtimeManager.newSession(message);
            case "/stop" -> runtimeManager.stop(message);
            default -> botClient.sendMessage(message.chat().id(), "未知命令。发送 /help 查看可用命令。");
        }
    }

    private String normalizeCommand(String text) {
        String head = text.split("\\s+", 2)[0];
        int botSuffix = head.indexOf('@');
        return botSuffix >= 0 ? head.substring(0, botSuffix) : head;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closed = true;
        }
    }

    /**
     * 请求退出 polling。
     */
    @Override
    public void close() {
        closed = true;
    }
}
