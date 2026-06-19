package dev.omatheusmesmo.qlawkus.skill;

import java.util.List;
import java.util.Optional;

/**
 * Storage SPI for the agent's procedural memory (skills). Backed by Markdown files by default
 * ({@link MarkdownSkillStore}); a pgvector-backed implementation overrides it when
 * {@code qlawkus.cognition.backend} selects it. The index ({@link #index()}) is cheap and meant
 * to be injected every turn; full bodies are fetched on demand via {@link #get(String)}
 * (progressive disclosure).
 */
public interface SkillStore {

  /**
   * The skill index (name + description only) used for prompt injection. Excludes
   * {@link SkillState#ARCHIVED} skills so the prompt stays compact; archived skills remain
   * loadable via {@link #get(String)}.
   */
  List<SkillSummary> index();

  /** The full skill, including its Markdown body, or empty if no skill has that name. */
  Optional<Skill> get(String name);

  /**
   * Creates or replaces a skill in the owned, writable root, and returns the saved skill.
   * Propagates a {@link RuntimeException} when persistence fails.
   */
  Skill save(Skill skill);

  /** Removes a skill from the owned root. Returns {@code true} if a skill was removed. */
  boolean delete(String name);

  /**
   * Records that a skill's body was consulted: bumps its usage count and last-used time and
   * revives it to {@link SkillState#ACTIVE}. Best-effort: never throws, and is a no-op for
   * read-only bundled skills.
   */
  void recordUse(String name);

  /**
   * Pins or unpins a skill. Pinned skills are never auto-transitioned by
   * {@link #sweepLifecycle(int, int)}. Returns {@code true} if the skill exists.
   */
  boolean setPinned(String name, boolean pinned);

  /**
   * Transitions skills by recency: those unused beyond {@code staleAfterDays} become
   * {@link SkillState#STALE}, those unused beyond {@code archiveAfterDays} become
   * {@link SkillState#ARCHIVED}. Pinned skills are skipped. Returns the number of skills whose
   * state changed.
   */
  int sweepLifecycle(int staleAfterDays, int archiveAfterDays);
}
