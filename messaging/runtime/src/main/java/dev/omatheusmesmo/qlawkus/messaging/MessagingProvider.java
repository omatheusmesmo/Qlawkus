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

    /** Completes when the platform acknowledges the file. Unsupported adapters report a failure. */
    default Uni<Void> sendFile(String chatId, String filename, String mediaType, byte[] bytes) {
        return Uni.createFrom().failure(new UnsupportedOperationException("This provider does not support file delivery"));
    }

    /**
     * Shows the owner an approval request with Approve and Deny buttons, when the platform has them.
     * Completes with {@code true} once the prompt (text and buttons) was delivered, and with
     * {@code false} when this platform cannot offer buttons, so the caller sends the plain text that tells
     * the owner to reply with a command. A pressed button must reach the orchestrator as the typed
     * command (see {@code ApprovalCallbacks}), never decide on its own.
     */
    default Uni<Boolean> sendApprovalPrompt(String chatId, String requestId, String text) {
        return Uni.createFrom().item(false);
    }

    /**
     * The status-message operations for showing tool activity, or empty when the platform cannot
     * edit its own messages. The returned sink is called from the turn's worker thread.
     */
    default Optional<ToolProgressSink> progressSink() {
        return Optional.empty();
    }

    /**
     * What the platform reported for a failed {@link #send(String, String)}, or empty when the failure
     * carries nothing the platform said. Adapters override this for their own client's exception types.
     */
    default Optional<DeliveryFailure> deliveryFailure(Throwable failure) {
        return Optional.empty();
    }
}
