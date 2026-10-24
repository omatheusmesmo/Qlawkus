package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.exception.HttpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * The recent outcome of the primary provider per surface and workload, kept for readiness.
 *
 * <p>A permanent answer (a retired model, a revoked key, a wrong model name) is skipped by every
 * fault-tolerance strategy on purpose: retrying it cannot help and falling back would hide it. That
 * same choice leaves the breaker CLOSED while every turn fails, so the breaker cannot be the signal
 * for it. This counts consecutive permanent failures instead, fed by the guards around each attempt
 * against the primary, and {@code ModelReadinessCheck} reads it. A success resets the count; a
 * transient failure leaves it alone, since it says nothing about whether the key or the model came
 * back.
 *
 * <p>Classified by the HTTP status the provider answered, which langchain4j keeps as the {@link
 * HttpException} cause of the exception it maps, so the decision does not depend on how a given
 * client names its exceptions.
 */
@ApplicationScoped
public class PermanentFailures {

    /** Consecutive permanent failures on one surface, with the status and time of the latest. */
    public record Streak(int consecutive, int status, Instant lastFailure) {

        static final Streak NONE = new Streak(0, 0, null);

        /**
         * Whether this streak means the surface cannot serve now: enough consecutive failures, the
         * latest still recent enough. The window lets readiness turn UP again so traffic can probe a
         * provider that may have come back, rather than holding an instance out of rotation forever.
         */
        public boolean blocks(int threshold, Duration window, Instant now) {
            return consecutive >= threshold && lastFailure != null && lastFailure.plus(window).isAfter(now);
        }

        Streak failedWith(int failedStatus, Instant at) {
            return new Streak(consecutive + 1, failedStatus, at);
        }
    }

    private final ConcurrentMap<String, Streak> streaks = new ConcurrentHashMap<>();
    private final Set<Integer> statuses;

    @Inject
    public PermanentFailures(ModelFallbackConfig config) {
        this.statuses = Set.copyOf(config.permanentFailure().statuses());
    }

    /**
     * Runs one attempt against the primary and records its outcome. The exception is rethrown as it
     * was: this only watches, it decides nothing about retry or fallback.
     */
    public <T> T observe(String surface, String workload, Supplier<T> attempt) {
        T result;
        try {
            result = attempt.get();
        } catch (RuntimeException e) {
            permanentStatus(e).ifPresent(status -> streaks.compute(key(surface, workload),
                    (key, streak) -> (streak == null ? Streak.NONE : streak).failedWith(status, Instant.now())));
            throw e;
        }
        streaks.remove(key(surface, workload));
        return result;
    }

    /** The current streak for a surface and workload; {@code consecutive} is 0 when there is none. */
    public Streak streak(String surface, String workload) {
        return streaks.getOrDefault(key(surface, workload), Streak.NONE);
    }

    /** The provider's status when it is one of the configured permanent ones, anywhere in the chain. */
    OptionalInt permanentStatus(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http && statuses.contains(http.statusCode())) {
                return OptionalInt.of(http.statusCode());
            }
        }
        return OptionalInt.empty();
    }

    private static String key(String surface, String workload) {
        return surface + "/" + workload;
    }
}
