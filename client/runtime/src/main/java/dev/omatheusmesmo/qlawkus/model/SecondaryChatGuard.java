package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.smallrye.faulttolerance.api.TypedGuard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The guard of the secondary chat tier: the same-provider model tried after the primary gives up and
 * before the Ollama fallback. It has its own circuit breaker per workload, so a provider outage that
 * opens the primary's breaker stops calls waiting on the secondary once its own breaker opens, and
 * they go straight to Ollama. Its retries are deliberately short: a call that reaches the secondary has
 * already spent the primary's retry budget, and a further long retry loop would only delay the switch to
 * the local model.
 *
 * <p>Like {@link PrimaryChatGuard} it runs every attempt on the calling thread and passes the next tier
 * through a {@link ThreadLocal}. Its fallback is always the Ollama tier, so a secondary failure is never
 * an answer by itself.
 *
 * <p>Unlike the primary, a permanent answer (a retired model, a key no longer entitled to it) is not
 * retried but still falls through and still counts towards the breaker. The primary surfaces such an
 * error because it means the deployment is misconfigured; here the primary has already failed, so
 * surfacing the secondary's error would turn a degraded reply into none at all, on every turn of the
 * outage.
 */
@ApplicationScoped
@Startup
public class SecondaryChatGuard {

    static final int RETRIES = 1;

    private final Map<String, TypedGuard<ChatResponse>> guards = new LinkedHashMap<>();
    private final WorkloadGuards workloads;
    private final AgentMeters meters;
    private final ThreadLocal<PrimaryChatGuard.Tier> currentFallback = new ThreadLocal<>();

    @Inject
    public SecondaryChatGuard(ModelFallbackConfig config, AgentMeters meters, WorkloadGuards workloads) {
        this.meters = meters;
        this.workloads = workloads;
        for (String workload : workloads.names()) {
            guards.put(workload, buildGuard(config, workload));
        }
    }

    private TypedGuard<ChatResponse> buildGuard(ModelFallbackConfig config, String workload) {
        return TypedGuard.create(ChatResponse.class)
                .withRetry()
                .maxRetries(RETRIES)
                .abortOn(PrimaryChatGuard.RETRY_ABORTS_ON)
                .delay(config.retryInitialDelay().toMillis(), ChronoUnit.MILLIS)
                .withExponentialBackoff()
                .maxDelay(config.retryMaxDelay().toMillis(), ChronoUnit.MILLIS)
                .done()
                .done()
                .withCircuitBreaker()
                .name(workloads.breakerName(WorkloadGuards.CHAT_SECONDARY, workload))
                .requestVolumeThreshold(RETRIES + 1)
                .failureRatio(1.0)
                .delay(config.circuitBreakerResetTimeout().toMillis(), ChronoUnit.MILLIS)
                .done()
                .withFallback()
                .handler(cause -> onFallback())
                .done()
                .build();
    }

    /**
     * Runs the secondary model with its retries and breaker, and on failure (or an open breaker) serves
     * {@code ollama}. The switch is metered as the Ollama tier.
     */
    public ChatResponse call(Supplier<ChatResponse> secondary, Supplier<ChatResponse> ollama) {
        String workload = resolve(WorkloadContext.current());
        currentFallback.set(new PrimaryChatGuard.Tier(AgentMeters.TIER_FALLBACK, ollama));
        try {
            return guards.get(workload).get(secondary);
        } finally {
            currentFallback.remove();
        }
    }

    private ChatResponse onFallback() {
        PrimaryChatGuard.Tier tier = currentFallback.get();
        meters.fallback(AgentMeters.SURFACE_CHAT, tier.name());
        return tier.serve().get();
    }

    private String resolve(String workload) {
        if (!guards.containsKey(workload)) {
            Log.warnf("Unknown model workload '%s', using %s", workload, WorkloadContext.INTERACTIVE);
            return WorkloadContext.INTERACTIVE;
        }
        return workload;
    }
}
