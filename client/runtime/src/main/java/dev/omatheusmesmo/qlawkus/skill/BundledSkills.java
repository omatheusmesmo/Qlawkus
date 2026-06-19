package dev.omatheusmesmo.qlawkus.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only skills shipped on the classpath ({@code META-INF/qlawkus-skills/<name>/SKILL.md}) by
 * the application or its extensions. Discovered and parsed at BUILD TIME and baked into this bean
 * via a recorder, so there is no classpath scanning or file reading at runtime. The active
 * {@link SkillStore} merges these (read-only) with the mutable, owned skills: runtime skills win
 * on a name clash, so a user can override a bundled skill.
 */
public final class BundledSkills {

  private final Map<String, Skill> byName = new LinkedHashMap<>();

  public BundledSkills(List<Skill> skills) {
    for (Skill skill : skills) {
      byName.putIfAbsent(skill.name(), skill);
    }
  }

  public Optional<Skill> get(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  /** Merges this bundled index after the given runtime index; runtime entries win on name clash. */
  public List<SkillSummary> mergedIndex(List<SkillSummary> runtime) {
    Map<String, SkillSummary> merged = new LinkedHashMap<>();
    for (SkillSummary summary : runtime) {
      merged.putIfAbsent(summary.name(), summary);
    }
    for (Skill skill : byName.values()) {
      merged.putIfAbsent(skill.name(), new SkillSummary(skill.name(), skill.description()));
    }
    return new ArrayList<>(merged.values());
  }
}
