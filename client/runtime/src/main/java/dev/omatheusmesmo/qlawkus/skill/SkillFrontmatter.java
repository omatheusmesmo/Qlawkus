package dev.omatheusmesmo.qlawkus.skill;

/**
 * Writer for SKILL.md files: renders a {@link Skill} into a {@code ---}-delimited YAML frontmatter
 * block ({@code name} + {@code description}) followed by the Markdown body. Reading/parsing is
 * delegated to the upstream {@code dev.langchain4j.skills} loaders (agentskills.io spec); this
 * class only owns the write side, which upstream does not provide.
 */
public final class SkillFrontmatter {

  private static final String DELIMITER = "---";

  private SkillFrontmatter() {
  }

  static String render(Skill skill) {
    StringBuilder sb = new StringBuilder();
    sb.append(DELIMITER).append('\n');
    sb.append("name: ").append(skill.name()).append('\n');
    sb.append("description: \"").append(escape(skill.description())).append("\"\n");
    sb.append(DELIMITER).append("\n\n");
    if (skill.body() != null && !skill.body().isBlank()) {
      sb.append(skill.body().strip()).append('\n');
    }
    return sb.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
  }
}
