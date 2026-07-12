package dev.omatheusmesmo.qlawkus.dto;

/**
 * The staged build-time config overrides, the {@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED}
 * counterpart of {@link CompositionState}. The build/restart tooling reads this over the API to learn
 * which property values the next rebuild should bake in, alongside the composition manifest.
 *
 * @param active the overrides currently baked into this running app ({@code qlawkus/config-overrides.properties}
 *               on the classpath), or {@code null} when none are present
 * @param staged the overrides staged for the next rebuild, or {@code null} when none is pending
 * @param stagedAt ISO-8601 instant the staged overrides were written, or {@code null} when none
 */
public record ConfigOverridesState(String active, String staged, String stagedAt) {
}
