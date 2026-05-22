package dev.omatheusmesmo.qlawkus.messaging;

import io.smallrye.mutiny.Uni;

public interface MessagingProvider {

    String providerId();

    MessagingFormat supportedFormat();

    Uni<MessagingResponse> receive(MessagingMessage message);

    Uni<Void> send(String chatId, String text);

    /**
     * Sends an audio reply. Providers that cannot deliver audio fall back to
     * sending {@code fallbackText} as a normal text message.
     */
    default Uni<Void> sendVoice(String chatId, byte[] audio, String filename, String fallbackText) {
        return send(chatId, fallbackText);
    }
}
