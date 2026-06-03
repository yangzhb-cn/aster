package com.aster;

import com.aster.ui.im.telegram.TelegramSessionMapStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telegram chat-session 映射存储测试。
 */
class TelegramSessionMapStoreTest {
    @TempDir
    Path tempDir;

    /**
     * 验证 chatId 到 sessionId 的映射可以持久化恢复。
     */
    @Test
    void persistsChatSessionMapping() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path file = tempDir.resolve("telegram-sessions.json");
        TelegramSessionMapStore store = new TelegramSessionMapStore(objectMapper, file);

        store.put(-100L, "tg_m100");

        TelegramSessionMapStore reopened = new TelegramSessionMapStore(objectMapper, file);
        assertEquals("tg_m100", reopened.get(-100L).orElseThrow());
        assertTrue(reopened.get(200L).isEmpty());
    }
}
