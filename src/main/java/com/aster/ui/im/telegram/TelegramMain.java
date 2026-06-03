package com.aster.ui.im.telegram;

import com.aster.app.runtime.AgentRuntimeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aster Telegram 入口。
 *
 * <p>Telegram 当前使用 long polling，不需要公网 webhook。
 * 它是 UI/IM 入口，只通过 AgentRuntime 与核心系统交互。</p>
 */
public final class TelegramMain {
    private TelegramMain() {
    }

    /**
     * 启动 Telegram Bot long polling。
     */
    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(20))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(20))
                .build();

        TelegramBotClient botClient = TelegramBotClient.fromToken(
                httpClient,
                objectMapper,
                requiredEnv("TELEGRAM_BOT_TOKEN")
        );
        TelegramRuntimeManager runtimeManager = new TelegramRuntimeManager(
                new AgentRuntimeFactory(),
                botClient,
                objectMapper
        );
        TelegramUpdatePoller poller = new TelegramUpdatePoller(
                botClient,
                runtimeManager,
                allowedChatIds()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            poller.close();
            runtimeManager.close();
        }));

        System.out.println("Aster Telegram polling started.");
        poller.runForever();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private static Set<Long> allowedChatIds() {
        String raw = requiredEnv("TELEGRAM_ALLOWED_CHAT_IDS");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }
}
