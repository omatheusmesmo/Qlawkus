package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class NotificationService {

    public enum DeliveryStatus { SENT, PARTIAL, FAILED, NO_PROVIDER }

    public record DeliveryReport(DeliveryStatus status, int acceptedParts, int totalParts,
                                 String errorType, Integer httpStatus) {}

    private static final String TEXT = "text";
    private static final String VOICE = "voice";

    @Inject
    ProviderRegistry providerRegistry;

    @Inject
    FormatterRegistry formatterRegistry;

    @Inject
    SecretRedactor redactor;

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
        return sendTracked(providerId, chatId, text, conversation).replaceWithVoid();
    }

    public Uni<DeliveryReport> sendTracked(String providerId, String chatId, String text, String conversation) {
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
                    return Uni.createFrom().item(new DeliveryReport(DeliveryStatus.NO_PROVIDER, 0, 0, null, null));
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
                                .onFailure().invoke(err -> logFailed(providerId, chatId, conversation,
                                        VOICE, 0, 1, err, startNanos, failureDetail(provider, err)))))
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

    private Uni<DeliveryReport> sendParts(String providerId, MessagingProvider provider, String chatId, List<String> parts,
                                         String conversation) {
        return Uni.createFrom().item(System::nanoTime).flatMap(startNanos -> {
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            return Multi.createFrom().iterable(parts)
                    .onItem().transformToUniAndConcatenate(part -> Uni.createFrom().deferred(() -> provider.send(chatId, part))
                            .map(ignored -> Boolean.TRUE)
                            .onFailure().invoke(err -> firstFailure.compareAndSet(null, err))
                            .onFailure().recoverWithItem(Boolean.FALSE))
                    .collect().asList()
                    .map(outcomes -> {
                        int sent = (int) outcomes.stream().filter(Boolean::booleanValue).count();
                        Throwable failure = firstFailure.get();
                        if (failure == null) {
                            logDelivered(providerId, chatId, conversation, TEXT, sent, startNanos);
                            return new DeliveryReport(DeliveryStatus.SENT, sent, outcomes.size(), null, null);
                        }
                        var detail = failureDetail(provider, failure);
                        logFailed(providerId, chatId, conversation, TEXT, sent, outcomes.size(), failure, startNanos, detail);
                        Integer status = detail.map(DeliveryFailure::httpStatus).orElse(null);
                        return new DeliveryReport(sent == 0 ? DeliveryStatus.FAILED : DeliveryStatus.PARTIAL,
                                sent, outcomes.size(), MessagingLog.exceptionName(failure), status);
                    });
        });
    }

    private static void logDelivered(String providerId, String chatId, String conversation, String kind,
                                     long parts, long startNanos) {
        MessagingLog.info(providerId, conversation,
                "Reply delivered provider=%s chatId=%s parts=%d kind=%s elapsedMs=%d",
                providerId, chatId, parts, kind, elapsedMs(startNanos));
    }

    private void logFailed(String providerId, String chatId, String conversation, String kind, long sent,
                           long total, Throwable failure, long startNanos, Optional<DeliveryFailure> detail) {
        MessagingLog.warn(providerId, conversation,
                "Reply delivery failed provider=%s chatId=%s kind=%s sentParts=%d of %d exception=%s elapsedMs=%d%s",
                providerId, chatId, kind, sent, total, MessagingLog.exceptionName(failure), elapsedMs(startNanos),
                describeDelivery(detail));
    }

    private static Optional<DeliveryFailure> failureDetail(MessagingProvider provider, Throwable failure) {
        return provider.deliveryFailure(failure).or(() -> DeliveryFailure.fromHttpStatus(failure));
    }

    private String describeDelivery(Optional<DeliveryFailure> detail) {
        return detail.map(value -> value.describe(redactor::redact))
                .filter(text -> !text.isEmpty())
                .map(text -> " " + text)
                .orElse("");
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
