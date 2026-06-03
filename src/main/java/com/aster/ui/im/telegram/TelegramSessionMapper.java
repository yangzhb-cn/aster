package com.aster.ui.im.telegram;

import com.aster.ui.im.telegram.model.TelegramChat;
import com.aster.ui.im.telegram.model.TelegramMessage;

/**
 * Telegram chat 与 Aster session 的映射规则。
 */
public final class TelegramSessionMapper {
    private TelegramSessionMapper() {
    }

    /**
     * 把 Telegram chatId 转成安全的 sessionId。
     */
    public static String sessionIdFor(long chatId) {
        String raw = String.valueOf(chatId);
        if (raw.startsWith("-")) {
            raw = "m" + raw.substring(1);
        }
        return "tg_" + raw;
    }

    /**
     * 生成 Telegram session 的展示名。
     */
    public static String displayNameFor(TelegramMessage message) {
        TelegramChat chat = message.chat();
        String name = firstNonBlank(
                chat.title(),
                chat.username() == null ? null : "@" + chat.username(),
                joinName(chat.firstName(), chat.lastName()),
                "chat " + chat.id()
        );
        return "Telegram " + name;
    }

    private static String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "chat";
    }
}
