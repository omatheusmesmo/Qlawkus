package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.metrics.CircuitStates;
import io.quarkus.runtime.Startup;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.TypedGuard;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

/**
 * The embedding counterpart of {@link PrimaryChatGuard}: retry + circuit breaker + fallback for
 * {@link FallbackEmbeddingModel}, kept as its own guard because embeddings and chat completions are
 * different upstream endpoints (see {@link ModelFallbackConfig#CIRCUIT_BREAKER_EMBEDDING}).
 *
 * <p>{@code embed}, {@code embed(segment)} and {@code embedAll} return different {@code Response<?>}
 * shapes, but a {@code TypedGuard} is fixed to one {@code T}. This guard is typed {@code <Object>};
 * {@link FallbackEmbeddingModel} casts at the one narrow boundary rather than building three guards
 * that would fragment the breaker three ways. Living in its own {@code @ApplicationScoped @Startup}
 * bean - the same shape as {@link PrimaryChatGuard} - also means a test can inject and drive the real,
 * already-registered guard directly instead of constructing a second one under the same circuit
 * breaker name, which {@code TypedGuard} rejects.
 */
@ApplicationScoped
@Startup
public class EmbeddingGuard {

    private static final List<Class<? extends Throwable>> NON_RETRYABLE =
            List.of(NonRetriableException.class, UnsupportedFeatureException.class);

    private final TypedGuard<Object> guard;
    private final AgentMeters meters;
    private final ThreadLocal<Supplier<Object>> currentFallback = new ThreadLocal<>();

    @Inject
    public EmbeddingGuard(ModelFallbackConfig config, AgentMeters meters) {
        this.meters = meters;
        List<Class<? extends Throwable>> abortsOn = new ArrayList<>(NON_RETRYABLE);
        abortsOn.add(CircuitBreakerOpenException.class);

        this.guard = TypedGuard.create(Object.class)
                .withRetry()
                .maxRetries(config.retryMaxAttempts())
                .abortOn(List.copyOf(abortsOn))
                .delay(config.retryInitialDelay().toMillis(), ChronoUnit.MILLIS)
                .withExponentialBackoff()
                .maxDelay(config.retryMaxDelay().toMillis(), ChronoUnit.MILLIS)
                .done()
                .done()
                .withCircuitBreaker()
                .name(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING)
                .requestVolumeThreshold(config.retryMaxAttempts() + 1)
                .failureRatio(1.0)
                .delay(config.circuitBreakerResetTimeout().toMillis(), ChronoUnit.MILLIS)
                .skipOn(NON_RETRYABLE)
                .done()
                .withFallback()
                .skipOn(NON_RETRYABLE)
                .handler(cause -> onFallback())
                .done()
                .build();
    }

    /**
     * Registers the breaker state as a gauge, read on scrape through {@code CircuitBreakerMaintenance},
     * a read-only lookup, so polling can never promote a state as a side effect. The gauge is bound to
     * this bean because Micrometer holds only a weak reference to the gauged object, and this
     * {@code @ApplicationScoped} instance outlives the registry.
     */
    @PostConstruct
    void publishCircuitState() {
        meters.circuitState(AgentMeters.SURFACE_EMBEDDING, this, guard -> CircuitStates.code(
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING)));
    }

    /**
     * The fallback handler, and therefore the exact moment the primary provider is abandoned for this
     * call. Counting here rather than inspecting the breaker means a switch is recorded even when the
     * circuit never opens, which is the common case for a single transient failure.
     */
    private Object onFallback() {
        meters.fallback(AgentMeters.SURFACE_EMBEDDING);
        return currentFallback.get().get();
    }

    @SuppressWarnings("unchecked")
    public <T> T call(Supplier<T> primary, Supplier<T> fallback) {
        currentFallback.set((Supplier<Object>) fallback);
        try {
            return (T) guard.get((Supplier<Object>) primary);
        } finally {
            currentFallback.remove();
        }
    }
}
