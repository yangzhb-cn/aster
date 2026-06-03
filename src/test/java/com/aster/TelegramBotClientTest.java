package com.aster;

import com.aster.ui.im.telegram.TelegramBotClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telegram Bot API 客户端测试。
 */
class TelegramBotClientTest {
    /**
     * 验证 getUpdates 能解析文本消息。
     */
    @Test
    void parsesGetUpdatesResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"ok":true,"result":[{"update_id":10,"message":{"message_id":7,"chat":{"id":123,"type":"private","first_name":"Ada"},"text":"hi"}}]}
                            """));
            TelegramBotClient client = new TelegramBotClient(
                    new OkHttpClient(),
                    new ObjectMapper(),
                    server.url("/botTEST/").toString()
            );

            var updates = client.getUpdates(5, 1);

            assertEquals(1, updates.size());
            assertEquals(10L, updates.getFirst().updateId());
            assertEquals("hi", updates.getFirst().message().text());
            assertTrue(server.takeRequest().getPath().contains("/botTEST/getUpdates"));
        }
    }

    /**
     * 验证超长消息会按 Telegram 限制拆成多条发送。
     */
    @Test
    void splitsLongMessages() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":{}}"));
            server.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":{}}"));
            TelegramBotClient client = new TelegramBotClient(
                    new OkHttpClient(),
                    new ObjectMapper(),
                    server.url("/botTEST/").toString()
            );

            client.sendMessage(123L, "x".repeat(4_100));

            assertTrue(server.takeRequest().getBody().readUtf8().contains("\"chat_id\":123"));
            assertTrue(server.takeRequest().getBody().readUtf8().contains("\"chat_id\":123"));
        }
    }
}
