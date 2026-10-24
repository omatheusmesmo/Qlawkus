package dev.omatheusmesmo.qlawkus.dto;

/**
 * How many runs ended one way, counted since the history began.
 *
 * @param outcome {@code success} or {@code failure}
 * @param failedPhase the phase that failed, or {@code none} on a success
 * @param count runs that ended this way
 */
public record RedeployTotal(String outcome, String failedPhase, long count) {}
