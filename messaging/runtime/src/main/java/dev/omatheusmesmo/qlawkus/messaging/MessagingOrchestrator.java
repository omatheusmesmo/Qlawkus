package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MessagingOrchestrator {

    @Inject
    AgentService agentService;

    @Inject
    MessagingAuthService authService;

    @Inject
    NotificationService notificationService;

    @Inject
    VoiceTranscriptionService transcriptionService;

    public Uni<Void> process(MessagingMessage message) {
        if (!authService.isAuthorized(message)) {
            Log.warnf("MessagingOrchestrator: unauthorized userId=%s provider=%s",
                    message.userId(), message.providerId());
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom()
                .item(() -> resolveText(message))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(text -> agentService.chatSync(text))
                .onFailure().invoke(err -> Log.errorf(err,
                        "MessagingOrchestrator: processing failed for userId=%s", message.userId()))
                .onFailure().recoverWithItem("I'm sorry, I couldn't process your message right now.")
                .flatMap(response -> notificationService.send(
                        message.providerId(), message.chatId(), response));
    }

    private String resolveText(MessagingMessage message) {
        return message.audio().map(audio -> {
            try {
                return transcriptionService.transcribe(audio);
            } catch (Exception e) {
                Log.warnf(e, "MessagingOrchestrator: transcription failed for userId=%s, using text fallback",
                        message.userId());
                return message.text();
            }
        }).orElse(message.text());
    }
}
