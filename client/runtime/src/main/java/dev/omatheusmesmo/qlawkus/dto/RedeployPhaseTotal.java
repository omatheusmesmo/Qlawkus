package dev.omatheusmesmo.qlawkus.dto;

/**
 * Time spent in one phase, accumulated since the history began and split by whether that phase was
 * the one that failed.
 *
 * @param phase the contract phase
 * @param outcome {@code failure} when this phase failed the run, {@code success} otherwise
 * @param count runs that went through this phase with this outcome
 * @param totalMillis milliseconds spent across those runs
 */
public record RedeployPhaseTotal(String phase, String outcome, long count, long totalMillis) {}
