package dev.omatheusmesmo.qlawkus.skill;

/**
 * Per-skill usage telemetry persisted by the markdown backend in a sidecar file. Times are epoch
 * milliseconds so the JSON round-trips without a Jackson time module. {@code lastUsedEpochMs} is
 * {@code 0} when the skill has never been used.
 */
public record SkillUsage(long lastUsedEpochMs, long useCount, SkillState state, boolean pinned) {

  public static SkillUsage initial() {
    return new SkillUsage(0L, 0L, SkillState.ACTIVE, false);
  }

  public SkillUsage usedAt(long epochMs) {
    return new SkillUsage(epochMs, useCount + 1, SkillState.ACTIVE, pinned);
  }

  public SkillUsage withState(SkillState newState) {
    return new SkillUsage(lastUsedEpochMs, useCount, newState, pinned);
  }

  public SkillUsage withPinned(boolean newPinned) {
    return new SkillUsage(lastUsedEpochMs, useCount, state, newPinned);
  }
}
