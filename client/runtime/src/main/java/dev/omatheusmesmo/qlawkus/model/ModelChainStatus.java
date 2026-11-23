package dev.omatheusmesmo.qlawkus.model;

import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the console shows about the model chain: each tier with its breaker state, the permanent
 * failures that hold the primary out without ever opening a breaker, and the tier that answered the
 * owner last. Everything is read from state the guards already keep, so reading it never calls a
 * model and never promotes a breaker. Only the interactive workload is reported, the same one
 * readiness is tied to.
 */
@ApplicationScoped
public class ModelChainStatus {

    private static final String NO_BREAKER = "-";

    private final ModelFallbackConfig config;
    private final WorkloadGuards workloads;
    private final PermanentFailures failures;

    public ModelChainStatus(ModelFallbackConfig config, WorkloadGuards workloads, PermanentFailures failures) {
        this.config = config;
        this.workloads = workloads;
        this.failures = failures;
    }

    /** One tier of the chain; {@code state} is the breaker state, or {@code -} for a tier with none. */
    public record Tier(String name, boolean enabled, String state) {
    }

    /** A run of permanent answers that has the primary of {@code surface} held out. */
    public record Permanent(String surface, int status, int consecutive) {
    }

    public record Served(String tier, Instant at) {
    }

    public record Snapshot(List<Tier> tiers, List<Permanent> permanent, Optional<Served> lastServed) {

        public Tier tier(String name) {
            return tiers.stream().filter(tier -> tier.name().equals(name)).findFirst().orElseThrow();
        }
    }

    public Snapshot snapshot() {
        List<Tier> tiers = List.of(
                new Tier(ServedTier.PRIMARY, true, stateOf(WorkloadGuards.CHAT)),
                new Tier(ServedTier.SECONDARY, config.secondaryEnabled(), stateOf(WorkloadGuards.CHAT_SECONDARY)),
                new Tier(ServedTier.FALLBACK, config.fallbackEnabled(), NO_BREAKER));
        List<Permanent> permanent = new ArrayList<>();
        for (String surface : List.of(WorkloadGuards.CHAT, WorkloadGuards.EMBEDDING)) {
            PermanentFailures.Streak streak = failures.streak(surface, WorkloadContext.INTERACTIVE);
            ModelFallbackConfig.PermanentFailure policy = config.permanentFailure();
            if (streak.blocks(policy.threshold(), policy.window(), Instant.now())) {
                permanent.add(new Permanent(surface, streak.status(), streak.consecutive()));
            }
        }
        Optional<Served> served = ServedTier.lastServed().map(last -> new Served(last.tier(), last.at()));
        return new Snapshot(tiers, List.copyOf(permanent), served);
    }

    private String stateOf(String surface) {
        return CircuitBreakerMaintenance.get()
                .currentState(workloads.breakerName(surface, WorkloadContext.INTERACTIVE)).name();
    }
}
