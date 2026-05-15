package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class NotificationService {

    @Inject
    ProviderRegistry providerRegistry;

    @Inject
    FormatterRegistry formatterRegistry;

    @Inject
    ChunkingService chunkingService;

    public Uni<Void> send(String providerId, String chatId, String text) {
        return providerRegistry.getProvider(providerId)
                .map(provider -> {
                    MessageFormatter formatter = formatterRegistry.forFormat(provider.supportedFormat());
                    String formatted = formatter.format(text);
                    List<String> chunks = chunkingService.chunk(formatted, providerId);
                    return Multi.createFrom().iterable(chunks)
                            .onItem().transformToUniAndConcatenate(chunk -> provider.send(chatId, chunk))
                            .toUni().replaceWithVoid();
                })
                .orElseGet(() -> {
                    Log.warnf("NotificationService: no active provider=%s, dropping message to chatId=%s", providerId, chatId);
                    return Uni.createFrom().voidItem();
                });
    }

    public Uni<Void> broadcast(String text) {
        return Multi.createFrom().iterable(providerRegistry.activeProviders())
                .onItem().transformToUniAndConcatenate(provider ->
                        send(provider.providerId(), null, text))
                .toUni().replaceWithVoid();
    }
}
