package dev.omatheusmesmo.qlawkus.messaging.discord;

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
public class DiscordProviderAdapter implements MessagingProvider {

    @Inject
    DiscordConfig config;

    @Inject
    @RestClient
    DiscordApiClient apiClient;

    @Inject
    MessagingOrchestrator orchestrator;

    @Override
    public String providerId() {
        return "discord";
    }

    @Override
    public MessagingFormat supportedFormat() {
        return MessagingFormat.DISCORD_MARKDOWN;
    }

    @Override
    public Uni<MessagingResponse> receive(MessagingMessage message) {
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String interactionToken, String text) {
        return Uni.createFrom()
                .item(() -> {
                    apiClient.editOriginalResponse(
                            config.applicationId(),
                            interactionToken,
                            new DiscordApiClient.EditMessageRequest(text),
                            "Bot " + config.botToken());
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err, "Discord send failed token=%s", interactionToken))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    public MessagingMessage mapInteraction(DiscordInteraction interaction) {
        String userId = interaction.resolvedUserId();
        String channelId = interaction.channelId() != null ? interaction.channelId() : userId;
        String text = interaction.resolvedText();
        return new MessagingMessage("discord", channelId, userId, text, Optional.empty());
    }
}
