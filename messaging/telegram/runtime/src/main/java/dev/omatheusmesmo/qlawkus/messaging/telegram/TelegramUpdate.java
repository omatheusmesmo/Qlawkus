package dev.omatheusmesmo.qlawkus.messaging.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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
            TelegramVoice voice,
            String caption,
            TelegramDocument document,
            List<TelegramPhoto> photo
    ) {
        public TelegramMessage(long messageId, TelegramUser from, TelegramChat chat, String text, TelegramVoice voice) {
            this(messageId, from, chat, text, voice, null, null, null);
        }

        boolean hasAttachments() {
            return document != null || photo != null && !photo.isEmpty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramDocument(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_size") Long size
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramPhoto(
            @JsonProperty("file_id") String fileId,
            @JsonProperty("file_size") Long size
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
