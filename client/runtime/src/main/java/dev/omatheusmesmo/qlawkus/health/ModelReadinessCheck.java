package dev.omatheusmesmo.qlawkus.health;

import dev.omatheusmesmo.qlawkus.model.ModelFallbackConfig;
import dev.omatheusmesmo.qlawkus.model.PermanentFailures;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.model.WorkloadGuards;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.time.Instant;

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
 * <p>A breaker never sees a permanent answer (a retired model, a revoked key, a wrong model name):
 * retrying it cannot help and falling back would hide it, so the guards skip both and the breaker
 * stays CLOSED while every turn fails. That case is read from {@link PermanentFailures} instead: a run
 * of consecutive permanent failures on either surface reports DOWN, naming the status, regardless of
 * the fallback, since a permanent failure never reaches it.
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

    @Inject
    WorkloadGuards workloads;

    @Inject
    PermanentFailures failures;

    /**
     * Readiness answers whether this instance can serve, so only the interactive workload decides the
     * status. A background breaker that opened is reported as data and nothing more: letting it turn
     * the pod unready would rebuild, in the health layer, exactly the coupling the workloads were
     * introduced to remove - a nightly job taking the instance out of rotation.
     */
    @Override
    public HealthCheckResponse call() {
        CircuitBreakerState chat = stateOf(WorkloadGuards.CHAT, WorkloadContext.INTERACTIVE);
        CircuitBreakerState embedding = stateOf(WorkloadGuards.EMBEDDING, WorkloadContext.INTERACTIVE);
        boolean fallbackEnabled = config.fallbackEnabled();
        boolean circuitBlocked =
                (chat == CircuitBreakerState.OPEN || embedding == CircuitBreakerState.OPEN) && !fallbackEnabled;

        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME)
                .withData("chatCircuit", chat.name())
                .withData("embeddingCircuit", embedding.name())
                .withData("fallbackEnabled", fallbackEnabled);
        Instant now = Instant.now();
        boolean chatBlocked = reportPermanent(response, WorkloadGuards.CHAT, now);
        boolean embeddingBlocked = reportPermanent(response, WorkloadGuards.EMBEDDING, now);
        response.status(!(circuitBlocked || chatBlocked || embeddingBlocked));

        for (String workload : workloads.names()) {
            if (WorkloadContext.INTERACTIVE.equals(workload)) {
                continue;
            }
            response.withData(workload + "ChatCircuit", stateOf(WorkloadGuards.CHAT, workload).name());
            response.withData(workload + "EmbeddingCircuit",
                    stateOf(WorkloadGuards.EMBEDDING, workload).name());
            response.withData(workload + "ChatPermanentFailures",
                    failures.streak(WorkloadGuards.CHAT, workload).consecutive());
            response.withData(workload + "EmbeddingPermanentFailures",
                    failures.streak(WorkloadGuards.EMBEDDING, workload).consecutive());
        }
        return response.build();
    }

    /**
     * Reports the interactive streak for one surface and returns whether it blocks serving. The error
     * names the status because the fix differs by it: a 410 means the model was retired, a 401 or
     * 403 a credential, a 404 a model name.
     */
    private boolean reportPermanent(HealthCheckResponseBuilder response, String surface, Instant now) {
        PermanentFailures.Streak streak = failures.streak(surface, WorkloadContext.INTERACTIVE);
        response.withData(surface + "PermanentFailures", streak.consecutive());
        ModelFallbackConfig.PermanentFailure policy = config.permanentFailure();
        if (!streak.blocks(policy.threshold(), policy.window(), now)) {
            return false;
        }
        response.withData(surface + "Error", "primary " + surface + " model answered HTTP " + streak.status()
                + " on " + streak.consecutive() + " consecutive calls");
        return true;
    }

    private CircuitBreakerState stateOf(String surface, String workload) {
        return CircuitBreakerMaintenance.get().currentState(workloads.breakerName(surface, workload));
    }
}
