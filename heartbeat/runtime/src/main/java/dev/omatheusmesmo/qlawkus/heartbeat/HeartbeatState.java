package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@RegisterForReflection
record HeartbeatState(HeartbeatSettings settings, String boundChat, long revision, Instant nextRun, LocalDate day,
                      int usedRuns, int failures, HeartbeatRun lastRun, Map<String, String> notices) {

    HeartbeatState {
        if (settings == null || day == null || usedRuns < 0 || failures < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid saved heartbeat state.");
        }
        notices = notices == null ? Map.of() : Map.copyOf(notices);
    }
}
