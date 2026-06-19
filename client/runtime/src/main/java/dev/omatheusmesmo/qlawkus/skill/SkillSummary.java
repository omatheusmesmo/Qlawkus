package dev.omatheusmesmo.qlawkus.skill;

/**
 * The lightweight index entry for a skill: only what is injected into the prompt every turn
 * ({@code name} + {@code description}). The full {@link Skill} body is fetched on demand.
 */
public record SkillSummary(String name, String description) {
}
