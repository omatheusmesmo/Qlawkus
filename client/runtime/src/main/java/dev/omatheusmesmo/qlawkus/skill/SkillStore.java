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

  /** The skill index (name + description only) used for prompt injection. */
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
}
