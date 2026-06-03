package com.aster;

import com.aster.ui.im.telegram.TelegramSessionMapper;
import com.aster.ui.im.telegram.model.TelegramChat;
import com.aster.ui.im.telegram.model.TelegramMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Telegram session 映射测试。
 */
class TelegramSessionMapperTest {
    /**
     * 验证负数群组 chatId 会转成安全 sessionId。
     */
    @Test
    void mapsChatIdToSafeSessionId() {
        assertEquals("tg_12345", TelegramSessionMapper.sessionIdFor(12345L));
        assertEquals("tg_m10012345", TelegramSessionMapper.sessionIdFor(-10012345L));
    }

    /**
     * 验证展示名优先使用群标题。
     */
    @Test
    void buildsDisplayNameFromChatTitle() {
        TelegramMessage message = new TelegramMessage(
                1L,
                null,
                new TelegramChat(-100L, "group", "Aster 群", null, null, null),
                "hello"
        );

        assertEquals("Telegram Aster 群", TelegramSessionMapper.displayNameFor(message));
    }
}
