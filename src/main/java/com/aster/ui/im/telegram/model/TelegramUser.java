package com.aster.ui.im.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram 用户基础信息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
        long id,
        @JsonProperty("is_bot") boolean bot,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username
) {
}
