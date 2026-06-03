package com.aster.ui.im.telegram;

import com.aster.ui.im.telegram.model.TelegramUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Telegram Bot API 客户端。
 *
 * <p>这里只封装 Bot API 的最小能力：拉取 update、发送文本和发送 typing 状态。
 * IM 层不直接拼 URL，也不把 Telegram HTTP 细节泄漏到 runtime。</p>
 */
public class TelegramBotClient implements TelegramMessageSender {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MESSAGE_LIMIT = 4096;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * 使用 Bot token 创建官方 API 客户端。
     */
    public static TelegramBotClient fromToken(OkHttpClient httpClient, ObjectMapper objectMapper, String token) {
        return new TelegramBotClient(httpClient, objectMapper, "https://api.telegram.org/bot" + requireToken(token) + "/");
    }

    public TelegramBotClient(OkHttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    /**
     * 使用 long polling 拉取 Telegram updates。
     */
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) throws IOException {
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(baseUrl + "getUpdates"))
                .newBuilder()
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("timeout", String.valueOf(timeoutSeconds))
                .addQueryParameter("allowed_updates", "[\"message\"]")
                .build();
        Request request = new Request.Builder().url(url).get().build();
        JsonNode result = execute(request);
        List<TelegramUpdate> updates = new ArrayList<>();
        for (JsonNode item : result) {
            updates.add(objectMapper.treeToValue(item, TelegramUpdate.class));
        }
        return updates;
    }

    /**
     * 发送文本消息；超过 Telegram 单条长度限制时自动分片。
     */
    @Override
    public void sendMessage(long chatId, String text) throws IOException {
        String value = text == null || text.isBlank() ? "(empty)" : text;
        int index = 0;
        while (index < value.length()) {
            int end = Math.min(index + MESSAGE_LIMIT, value.length());
            sendJson("sendMessage", Map.of(
                    "chat_id", chatId,
                    "text", value.substring(index, end)
            ));
            index = end;
        }
    }

    /**
     * 发送 typing 等轻量状态。
     */
    @Override
    public void sendChatAction(long chatId, String action) throws IOException {
        sendJson("sendChatAction", Map.of(
                "chat_id", chatId,
                "action", action
        ));
    }

    private void sendJson(String method, Map<String, Object> payload) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + method)
                .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), JSON))
                .build();
        execute(request);
    }

    private JsonNode execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Telegram API HTTP " + response.code() + ": " + body);
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("ok").asBoolean(false)) {
                throw new IOException("Telegram API error: " + body);
            }
            return root.path("result");
        }
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN is required");
        }
        return token.trim();
    }
}
