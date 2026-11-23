package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactDelivery;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class MessagingArtifactDelivery implements ArtifactDelivery {

    private final ProviderRegistry providers;

    MessagingArtifactDelivery(ProviderRegistry providers) {
        this.providers = providers;
    }

    @Override
    public Uni<Void> send(String providerId, String chatId, ArtifactLibrary.Content content) {
        return providers.getProvider(providerId)
                .map(provider -> provider.sendFile(chatId, content.handle().name(), content.handle().mediaType(), content.bytes()))
                .orElseGet(() -> Uni.createFrom().failure(new IllegalStateException("Messaging provider is unavailable")));
    }
}
