package dev.omatheusmesmo.qlawkus.approval;

/**
 * Lifecycle of one approval request. Every change is a compare-and-set from a known status, so a
 * replayed callback cannot move a request twice.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    EXECUTING,
    EXECUTED,
    FAILED,
    NEEDS_REVIEW;

    /** Whether the request is settled and may be dropped by retention. */
    public boolean isTerminal() {
        return this == DENIED || this == EXPIRED || this == EXECUTED || this == FAILED;
    }
}
