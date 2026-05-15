package dev.omatheusmesmo.qlawkus.messaging.slack;

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
public class SlackProviderAdapter implements MessagingProvider {

    @Inject
    SlackConfig config;

    @Inject
    @RestClient
    SlackApiClient apiClient;

    @Inject
    MessagingOrchestrator orchestrator;

    @Override
    public String providerId() {
        return "slack";
    }

    @Override
    public MessagingFormat supportedFormat() {
        return MessagingFormat.SLACK_MARKDOWN;
    }

    @Override
    public Uni<MessagingResponse> receive(MessagingMessage message) {
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String channel, String text) {
        return Uni.createFrom()
                .item(() -> {
                    apiClient.postMessage(
                            "Bearer " + config.botToken(),
                            new SlackApiClient.PostMessageRequest(channel, text, true));
                    return null;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().invoke(err -> Log.errorf(err, "Slack send failed channel=%s", channel))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    public MessagingMessage mapEvent(SlackEvent.InnerEvent event) {
        String text = event.text() != null ? event.text() : "";
        return new MessagingMessage("slack", event.channel(), event.user(), text, Optional.empty());
    }
}
