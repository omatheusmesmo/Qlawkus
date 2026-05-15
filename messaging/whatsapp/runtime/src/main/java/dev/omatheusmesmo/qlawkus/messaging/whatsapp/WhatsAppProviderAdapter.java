package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class WhatsAppProviderAdapter implements MessagingProvider {

    @Inject
    WhatsAppConfig config;

    @Inject
    @RestClient
    WhatsAppApiClient apiClient;

    @Inject
    MessagingOrchestrator orchestrator;

    @Override
    public String providerId() {
        return "whatsapp";
    }

    @Override
    public MessagingFormat supportedFormat() {
        return MessagingFormat.WHATSAPP_HTML;
    }

    @Override
    public Uni<MessagingResponse> receive(MessagingMessage message) {
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String to, String text) {
        return Uni.createFrom()
                .item(() -> {
                    apiClient.sendMessage(
                            config.phoneNumberId(),
                            "Bearer " + config.accessToken(),
                            WhatsAppApiClient.SendMessageRequest.textTo(to, text));
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err, "WhatsApp send failed to=%s", to))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    public MessagingMessage mapEvent(WhatsAppEvent.Message message) {
        String from = message.from();
        String text = message.text() != null ? message.text().body() : "";
        Optional<byte[]> audio = Optional.empty();
        return new MessagingMessage("whatsapp", from, from, text, audio);
    }
}
