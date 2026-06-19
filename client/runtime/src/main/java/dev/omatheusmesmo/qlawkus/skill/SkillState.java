package dev.omatheusmesmo.qlawkus.skill;

/**
 * Lifecycle state of a skill. {@code ACTIVE} skills appear in the injected index; {@code STALE}
 * ones are unused but still injected; {@code ARCHIVED} ones are excluded from the index (to keep
 * the prompt compact) but remain loadable on demand. Using a skill revives it to {@code ACTIVE}.
 */
public enum SkillState {
  ACTIVE,
  STALE,
  ARCHIVED
}
