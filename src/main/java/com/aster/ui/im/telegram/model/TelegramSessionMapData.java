package com.aster.ui.im.telegram.model;

import java.util.Map;

/**
 * workspace/im/telegram-sessions.json 的根对象。
 */
public record TelegramSessionMapData(
        Map<String, String> sessions
) {
    public TelegramSessionMapData {
        sessions = sessions == null ? Map.of() : Map.copyOf(sessions);
    }

    /**
     * 创建空映射。
     */
    public static TelegramSessionMapData empty() {
        return new TelegramSessionMapData(Map.of());
    }
}
