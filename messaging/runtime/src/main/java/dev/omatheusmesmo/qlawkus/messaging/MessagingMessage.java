package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import java.util.List;
import java.util.Optional;

public record MessagingMessage(
        String providerId,
        String chatId,
        String userId,
        String text,
        Optional<byte[]> audio,
        List<ChatAttachment> attachments
) {
    public MessagingMessage {
        attachments = List.copyOf(attachments);
    }

    public MessagingMessage(String providerId, String chatId, String userId, String text, Optional<byte[]> audio) {
        this(providerId, chatId, userId, text, audio, List.of());
    }

    public static MessagingMessage text(String providerId, String chatId, String userId, String text) {
        return new MessagingMessage(providerId, chatId, userId, text, Optional.empty());
    }

    public static MessagingMessage audio(String providerId, String chatId, String userId, byte[] audioBytes) {
        return new MessagingMessage(providerId, chatId, userId, "", Optional.of(audioBytes));
    }
}
