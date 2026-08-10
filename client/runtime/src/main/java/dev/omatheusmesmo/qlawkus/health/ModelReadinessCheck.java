package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.model.CircuitBreaker;
import dev.omatheusmesmo.qlawkus.model.ModelFallbackConfig;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports whether the agent still has a model it can reach, read from the circuit breaker the
 * fallback chain already maintains. Nothing here calls a model: a readiness probe runs every few
 * seconds, and an inference just to answer it would bill the owner for being monitored.
 *
 * <p>An open circuit alone is not unreadiness. The primary failing while the Ollama fallback serves
 * is degraded, not unable - taking the pod out of rotation there would turn a provider incident into
 * an outage. The check goes down only when the circuit is open <em>and</em> no fallback is
 * configured, which is the state where a request has nowhere left to go.
 *
 * <p>Because the breaker only opens after real failures, this reports UP on a fresh pod that has
 * never called anything. Startup reachability is a different question and is deliberately not
 * answered here.
 *
 * <p>State is read through {@code currentState()}, the breaker's only non-mutating accessor.
 * {@code isOpen()} promotes an expired OPEN to HALF_OPEN and logs it, so a probe polling every few
 * seconds would be driving the state machine it is supposed to be observing.
 */
@Readiness
public class ModelReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-model";

    @Inject
    CircuitBreaker circuitBreaker;

    @Inject
    ModelFallbackConfig config;

    @Override
    public HealthCheckResponse call() {
        CircuitBreaker.State state = circuitBreaker.currentState();
        boolean fallbackEnabled = config.fallbackEnabled();
        return HealthCheckResponse.named(NAME)
                .status(state != CircuitBreaker.State.OPEN || fallbackEnabled)
                .withData("circuit", state.name())
                .withData("fallbackEnabled", fallbackEnabled)
                .build();
    }
}
