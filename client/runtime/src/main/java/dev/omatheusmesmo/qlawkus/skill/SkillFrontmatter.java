package dev.omatheusmesmo.qlawkus.skill;

import java.util.Optional;

/**
 * Minimal reader/writer for SKILL.md files: a YAML frontmatter block delimited by {@code ---}
 * lines, followed by the Markdown body. Only the {@code name} and {@code description} keys are
 * read (the keys that form the injected index); other frontmatter keys are ignored. Avoids a
 * YAML dependency for what is, in practice, a couple of flat string keys.
 */
final class SkillFrontmatter {

  private static final String DELIMITER = "---";

  private SkillFrontmatter() {
  }

  record Parsed(Optional<String> name, Optional<String> description, String body) {
  }

  static Parsed parse(String content) {
    if (content == null) {
      return new Parsed(Optional.empty(), Optional.empty(), "");
    }
    String normalized = content.stripLeading();
    if (!normalized.startsWith(DELIMITER)) {
      return new Parsed(Optional.empty(), Optional.empty(), content.strip());
    }
    int firstBreak = normalized.indexOf('\n');
    if (firstBreak < 0) {
      return new Parsed(Optional.empty(), Optional.empty(), "");
    }
    int closing = normalized.indexOf("\n" + DELIMITER, firstBreak);
    if (closing < 0) {
      return new Parsed(Optional.empty(), Optional.empty(), content.strip());
    }
    String frontmatter = normalized.substring(firstBreak + 1, closing);
    int bodyStart = normalized.indexOf('\n', closing + 1);
    String body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1).strip();

    String name = null;
    String description = null;
    for (String line : frontmatter.split("\n")) {
      String trimmed = line.strip();
      if (trimmed.startsWith("name:")) {
        name = unquote(trimmed.substring("name:".length()).strip());
      } else if (trimmed.startsWith("description:")) {
        description = unquote(trimmed.substring("description:".length()).strip());
      }
    }
    return new Parsed(blankToEmpty(name), blankToEmpty(description), body);
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

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static Optional<String> blankToEmpty(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
