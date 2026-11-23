package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;

@RegisterForReflection
public record HeartbeatStatus(HeartbeatSettings settings, Instant nextRun, HeartbeatRun lastRun,
                              int minimumIntervalMinutes, int maximumDailyRuns, int remainingDailyRuns,
                              int maximumDecisionTokens, int deadlineSeconds, List<String> availableProviders,
                              boolean destinationAvailable, boolean storageAvailable) {
}
