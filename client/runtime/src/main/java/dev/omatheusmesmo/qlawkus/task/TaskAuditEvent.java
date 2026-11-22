package dev.omatheusmesmo.qlawkus.task;

import java.time.Instant;
import java.util.UUID;

/** A task lifecycle transition carrying correlation IDs and delivery counts only. */
public record TaskAuditEvent(String taskId, UUID runId, String provider, Stage stage,
                             Instant nextFire, String errorType, Integer httpStatus,
                             Integer acceptedParts, Integer totalParts) {
    public enum Stage {
        SCHEDULED, RESTORED, MISSED, INVALID, CANCELLED, STARTED, NO_REPLY, QUEUED, EXECUTION_FAILED,
        DELIVERY_SENT, DELIVERY_PARTIAL, DELIVERY_FAILED, DELIVERY_NO_PROVIDER
    }
}
