package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Parses SKILL.md text into a {@link Skill} and validates its name. Shared by every install path (a
 * fetched file, a repository folder, an archive entry, a gist and pasted text), so the same content is
 * judged the same way wherever it came from.
 */
final class SkillMarkdown {

  private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");

  private SkillMarkdown() {
  }

  /**
   * Parses {@code content}. The frontmatter {@code name} wins; {@code fallbackName} is used only when
   * the frontmatter has none, and a missing name is refused when there is no fallback either.
   *
   * @throws IllegalArgumentException when the name is missing or unsafe
   */
  static Skill parse(String content, String fallbackName) {
    Frontmatter fm = frontmatter(content);
    String name = fm.name().isBlank() ? (fallbackName == null ? "" : fallbackName) : fm.name();
    if (name.isBlank()) {
      throw new IllegalArgumentException("The SKILL.md has no name in its frontmatter. Add a 'name:' line "
          + "(lowercase words joined by hyphens) to the top of the file.");
    }
    if (!SAFE_NAME.matcher(name).matches() || name.contains("..")) {
      throw new IllegalArgumentException("Refusing to install skill with unsafe name: " + name
          + " (use letters, digits, '-', '_' and '.', starting with a letter or digit).");
    }
    return new Skill(name, fm.description(), fm.body());
  }

  /**
   * Parses SKILL.md text the owner pasted or uploaded, refusing it when it exceeds {@code maxBytes}
   * before anything is parsed.
   */
  static Skill parsePasted(String text, long maxBytes) {
    long bytes = text.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > maxBytes) {
      throw new IllegalArgumentException("The pasted SKILL.md is " + bytes + " bytes; the limit is "
          + maxBytes + " bytes.");
    }
    return parse(text, null);
  }

  static boolean isSafeName(String name) {
    return SAFE_NAME.matcher(name).matches() && !name.contains("..");
  }

  private static Frontmatter frontmatter(String content) {
    String normalized = content.stripLeading();
    if (!normalized.startsWith("---")) {
      return new Frontmatter("", "", content);
    }
    int end = normalized.indexOf("\n---", 3);
    if (end < 0) {
      return new Frontmatter("", "", content);
    }
    String header = normalized.substring(normalized.indexOf('\n') + 1, end);
    String body = normalized.substring(normalized.indexOf("\n", end + 1) + 1).stripLeading();
    String name = "";
    String description = "";
    for (String line : header.split("\n")) {
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).strip().toLowerCase();
      String value = unquote(line.substring(colon + 1).strip());
      if (key.equals("name")) {
        name = value;
      } else if (key.equals("description")) {
        description = value;
      }
    }
    return new Frontmatter(name, description, body);
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private record Frontmatter(String name, String description, String body) {
  }
}
