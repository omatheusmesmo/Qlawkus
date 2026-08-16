package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.model.ModelFallbackConfig;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the agent still has a model it can reach, read from the two circuit breakers the
 * fallback chain maintains - one for chat (shared by the sync and streaming transports, since both
 * hit the same completions endpoint), one for embeddings. Nothing here calls a model: a readiness
 * probe runs every few seconds, and an inference just to answer it would bill the owner for being
 * monitored.
 *
 * <p>Either circuit alone being open is not unreadiness. The primary failing while the Ollama
 * fallback serves is degraded, not unable - taking the pod out of rotation there would turn a
 * provider incident into an outage. The check goes down only when a circuit is open <em>and</em> no
 * fallback is configured, which is the state where a request on that surface has nowhere left to go.
 *
 * <p>Because a breaker only opens after real failures, this reports UP on a fresh pod that has never
 * called anything. Startup reachability is a different question and is deliberately not answered
 * here.
 *
 * <p>State is read through {@code CircuitBreakerMaintenance.currentState(name)}, a read-only lookup:
 * nothing here can promote a state as a side effect of being polled.
 */
@Readiness
public class ModelReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-model";

    @Inject
    ModelFallbackConfig config;

    @Override
    public HealthCheckResponse call() {
        CircuitBreakerState chat = CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
        CircuitBreakerState embedding =
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING);
        boolean fallbackEnabled = config.fallbackEnabled();
        boolean anyOpenWithNoFallback =
                (chat == CircuitBreakerState.OPEN || embedding == CircuitBreakerState.OPEN) && !fallbackEnabled;

        return HealthCheckResponse.named(NAME)
                .status(!anyOpenWithNoFallback)
                .withData("chatCircuit", chat.name())
                .withData("embeddingCircuit", embedding.name())
                .withData("fallbackEnabled", fallbackEnabled)
                .build();
    }
}
