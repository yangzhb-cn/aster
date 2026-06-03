package com.aster.ui.im.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram 文本消息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") long messageId,
        TelegramUser from,
        TelegramChat chat,
        String text
) {
}
