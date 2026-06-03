package com.aster.ui.im.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram chat 基础信息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
        long id,
        String type,
        String title,
        String username,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName
) {
}
