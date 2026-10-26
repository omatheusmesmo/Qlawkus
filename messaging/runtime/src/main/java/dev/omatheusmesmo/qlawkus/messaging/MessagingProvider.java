package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSink;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

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

    /**
     * The status-message operations for showing tool activity, or empty when the platform cannot
     * edit its own messages. The returned sink is called from the turn's worker thread.
     */
    default Optional<ToolProgressSink> progressSink() {
        return Optional.empty();
    }
}
