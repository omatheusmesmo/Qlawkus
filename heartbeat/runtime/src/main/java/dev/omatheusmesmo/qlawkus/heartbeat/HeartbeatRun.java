package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record HeartbeatRun(Instant startedAt, Instant finishedAt, Outcome outcome, int tokens,
                           Integer taskIssues, Boolean hasCuratedMemory) {

    public enum Outcome {
        RUNNING, INTERRUPTED, NO_OP, OBSERVED, NOTIFIED, NO_SIGNAL, DUPLICATE, DESTINATION_UNAVAILABLE,
        BUDGET_EXHAUSTED, TIMEOUT, DECISION_FAILED, OBSERVATION_FAILED, DELIVERY_FAILED,
        OVERLAP_SKIPPED, STORAGE_UNAVAILABLE
    }
}
