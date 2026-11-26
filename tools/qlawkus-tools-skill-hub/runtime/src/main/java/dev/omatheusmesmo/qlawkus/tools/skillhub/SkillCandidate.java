package dev.omatheusmesmo.qlawkus.tools.skillhub;

import java.util.List;

/**
 * One skill a source holds, before it is installed: a folder name in a repository or archive, or the
 * single skill a link names. {@code content} is set when the text was already read (archives, a named
 * skill); otherwise {@code location} is the URL {@link SkillHub#installCandidate} fetches. {@code scripts}
 * lists the files of the skill's {@code scripts/} folder the source holds, empty when it has none.
 */
public record SkillCandidate(String name, String source, String location, String content,
    List<SkillScript> scripts) {

  public SkillCandidate {
    scripts = scripts == null ? List.of() : List.copyOf(scripts);
  }

  public SkillCandidate(String name, String source, String location, String content) {
    this(name, source, location, content, List.of());
  }
}
