package dev.omatheusmesmo.qlawkus.messaging;

import java.util.Optional;

public record MessagingMessage(
        String providerId,
        String chatId,
        String userId,
        String text,
        Optional<byte[]> audio
) {
    public static MessagingMessage text(String providerId, String chatId, String userId, String text) {
        return new MessagingMessage(providerId, chatId, userId, text, Optional.empty());
    }

    public static MessagingMessage audio(String providerId, String chatId, String userId, byte[] audioBytes) {
        return new MessagingMessage(providerId, chatId, userId, "", Optional.of(audioBytes));
    }
}
