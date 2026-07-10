package dev.omatheusmesmo.qlawkus.dto;

/**
 * The composition state the admin endpoint exposes. The build/restart tooling reads this over the
 * API (the seam is HTTP, not a shared filesystem) to learn which manifest the app should be built
 * from next.
 *
 * @param active the manifest baked into this running app ({@code qlawkus/agent.yml} on the
 *               classpath), or {@code null} when the app ships no manifest
 * @param staged the manifest staged for the next rebuild, or {@code null} when none is pending
 * @param stagedAt ISO-8601 instant the staged manifest was written, or {@code null} when none
 */
public record CompositionState(String active, String staged, String stagedAt) {
}
