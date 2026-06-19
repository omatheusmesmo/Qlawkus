package dev.omatheusmesmo.qlawkus.skill;

/**
 * A skill loaded from a SKILL.md file. {@code name} and {@code description} come from the YAML
 * frontmatter and form the injected index; {@code body} is the full Markdown instructions,
 * loaded on demand (progressive disclosure).
 */
public record Skill(String name, String description, String body) {
}
