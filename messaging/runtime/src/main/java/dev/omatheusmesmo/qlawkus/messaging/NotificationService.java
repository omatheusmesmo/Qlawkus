package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class NotificationService {

    private static final String TEXT = "text";
    private static final String VOICE = "voice";

    @Inject
    ProviderRegistry providerRegistry;

    @Inject
    FormatterRegistry formatterRegistry;

    @Inject
    ChunkingService chunkingService;

    public Uni<Void> send(String providerId, String chatId, String text) {
        return send(providerId, chatId, text, null);
    }

    /**
     * Sends a text reply in as many parts as the platform needs. A failed part is logged and the remaining
     * parts are still sent, so the returned stage completes normally; the delivery line reports how many parts
     * the platform accepted.
     */
    public Uni<Void> send(String providerId, String chatId, String text, String conversation) {
        return providerRegistry.getProvider(providerId)
                .map(provider -> {
                    MessageFormatter formatter = formatterRegistry.forFormat(provider.supportedFormat());
                    String formatted = formatter.format(text);
                    List<String> chunks = chunkingService.chunk(formatted, providerId);
                    return sendParts(providerId, provider, chatId, chunks, conversation);
                })
                .orElseGet(() -> {
                    MessagingLog.warn(providerId, conversation,
                            "NotificationService: no active provider=%s, dropping message to chatId=%s",
                            providerId, chatId);
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Sends an audio reply. A failure is logged and then propagated, so the caller can fall back to text.
     */
    public Uni<Void> sendVoice(String providerId, String chatId, byte[] audio, String filename, String fallbackText,
                               String conversation) {
        return providerRegistry.getProvider(providerId)
                .map(provider -> Uni.createFrom().item(System::nanoTime)
                        .flatMap(startNanos -> provider.sendVoice(chatId, audio, filename, fallbackText)
                                .onItem().invoke(() -> logDelivered(providerId, chatId, conversation, VOICE, 1, startNanos))
                                .onFailure().invoke(err -> logFailed(providerId, chatId, conversation, VOICE, 0, 1,
                                        err, startNanos))))
                .orElseGet(() -> {
                    MessagingLog.warn(providerId, conversation,
                            "NotificationService: no active provider=%s, dropping voice message to chatId=%s",
                            providerId, chatId);
                    return Uni.createFrom().voidItem();
                });
    }

    public Uni<Void> broadcast(String text) {
        return Multi.createFrom().iterable(providerRegistry.activeProviders())
                .onItem().transformToUniAndConcatenate(provider ->
                        send(provider.providerId(), null, text))
                .toUni().replaceWithVoid();
    }

    private Uni<Void> sendParts(String providerId, MessagingProvider provider, String chatId, List<String> parts,
                                String conversation) {
        return Uni.createFrom().item(System::nanoTime).flatMap(startNanos -> {
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            return Multi.createFrom().iterable(parts)
                    .onItem().transformToUniAndConcatenate(part -> provider.send(chatId, part)
                            .map(ignored -> Boolean.TRUE)
                            .onFailure().invoke(err -> firstFailure.compareAndSet(null, err))
                            .onFailure().recoverWithItem(Boolean.FALSE))
                    .collect().asList()
                    .invoke(outcomes -> {
                        long sent = outcomes.stream().filter(Boolean::booleanValue).count();
                        if (firstFailure.get() == null) {
                            logDelivered(providerId, chatId, conversation, TEXT, sent, startNanos);
                        } else {
                            logFailed(providerId, chatId, conversation, TEXT, sent, outcomes.size(),
                                    firstFailure.get(), startNanos);
                        }
                    })
                    .replaceWithVoid();
        });
    }

    private static void logDelivered(String providerId, String chatId, String conversation, String kind,
                                     long parts, long startNanos) {
        MessagingLog.info(providerId, conversation,
                "Reply delivered provider=%s chatId=%s parts=%d kind=%s elapsedMs=%d",
                providerId, chatId, parts, kind, elapsedMs(startNanos));
    }

    private static void logFailed(String providerId, String chatId, String conversation, String kind,
                                  long sent, long total, Throwable failure, long startNanos) {
        MessagingLog.warn(providerId, conversation,
                "Reply delivery failed provider=%s chatId=%s kind=%s sentParts=%d of %d exception=%s elapsedMs=%d",
                providerId, chatId, kind, sent, total, MessagingLog.exceptionName(failure), elapsedMs(startNanos));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
