package com.aster.ui.im.telegram;

import java.io.IOException;

/**
 * Telegram 消息发送出口。
 *
 * <p>Agent 事件处理器只依赖这个小接口，测试时可以替换成内存实现，
 * 真正的 HTTP 请求由 TelegramBotClient 负责。</p>
 */
public interface TelegramMessageSender {
    /**
     * 发送一条文本消息。
     */
    void sendMessage(long chatId, String text) throws IOException;

    /**
     * 发送 Telegram chat action，例如 typing。
     */
    void sendChatAction(long chatId, String action) throws IOException;
}
