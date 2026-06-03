package com.aster.ui.im.telegram;

import com.aster.app.background.model.TaskRun;
import com.aster.app.notification.NotificationSink;

import java.io.IOException;
import java.util.Objects;

/**
 * Telegram 后台任务通知出口。
 */
public class TelegramNotificationSink implements NotificationSink {
    private final TelegramMessageSender sender;
    private final long chatId;

    public TelegramNotificationSink(TelegramMessageSender sender, long chatId) {
        this.sender = Objects.requireNonNull(sender);
        this.chatId = chatId;
    }

    @Override
    public void backgroundTaskCompleted(TaskRun run) {
        send("后台任务完成：" + run.taskName() + detail(run));
    }

    @Override
    public void backgroundTaskFailed(TaskRun run) {
        send("后台任务失败：" + run.taskName() + detail(run));
    }

    private String detail(TaskRun run) {
        String message = run.message();
        if (message == null || message.isBlank()) {
            return "";
        }
        if (message.length() > 800) {
            message = message.substring(0, 800) + "\n... 已截断 " + message.length() + " 字符";
        }
        return "\n" + message;
    }

    private void send(String text) {
        try {
            sender.sendMessage(chatId, text);
        } catch (IOException e) {
            System.err.println("Telegram notification failed: " + e.getMessage());
        }
    }
}
