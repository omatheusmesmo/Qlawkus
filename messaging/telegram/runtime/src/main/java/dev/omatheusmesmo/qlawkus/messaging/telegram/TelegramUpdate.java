package dev.omatheusmesmo.qlawkus.messaging.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        TelegramMessage message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            TelegramUser from,
            TelegramChat chat,
            String text,
            TelegramVoice voice
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(
            long id,
            @JsonProperty("first_name") String firstName,
            String username
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(
            long id,
            String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramVoice(
            @JsonProperty("file_id") String fileId,
            int duration
    ) {}
}
