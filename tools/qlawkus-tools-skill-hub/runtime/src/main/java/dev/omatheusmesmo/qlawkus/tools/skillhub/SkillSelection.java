package dev.omatheusmesmo.qlawkus.tools.skillhub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Chooses which of a source's skills to install from an owner's selection ({@code all}, or
 * comma-separated folder names) and enforces the per-call cap. Shared by the agent tool and the REST
 * install endpoint, so both refuse the same selections.
 */
final class SkillSelection {

  private SkillSelection() {
  }

  static List<SkillCandidate> select(List<SkillCandidate> found, String skills, String source) {
    if (skills.strip().equalsIgnoreCase("all")) {
      return found;
    }
    List<String> wanted = Arrays.stream(skills.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toLowerCase(Locale.ROOT))
        .toList();
    if (wanted.isEmpty()) {
      throw new IllegalArgumentException("Name the skills to install, comma-separated, or pass 'all'.");
    }
    List<SkillCandidate> selected = new ArrayList<>();
    for (String want : wanted) {
      SkillCandidate match = found.stream()
          .filter(c -> c.name().toLowerCase(Locale.ROOT).equals(want))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("'" + want + "' is not a skill in '" + source
              + "'. It holds: " + found.stream().map(SkillCandidate::name).collect(Collectors.joining(", "))
              + "."));
      if (!selected.contains(match)) {
        selected.add(match);
      }
    }
    return selected;
  }

  /**
   * Installs every candidate, attempting all of them even after a failure, then throws naming what was
   * installed and what failed, so a partial install is visible to the owner.
   */
  static List<String> installAll(SkillHub skillHub, List<SkillCandidate> selected) {
    List<String> installed = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    for (SkillCandidate candidate : selected) {
      try {
        installed.add(skillHub.installCandidate(candidate).name());
      } catch (RuntimeException e) {
        failed.add(candidate.name() + " (" + e.getMessage() + ")");
      }
    }
    if (!failed.isEmpty()) {
      throw new IllegalStateException("Installed " + (installed.isEmpty() ? "nothing" : String.join(", ", installed))
          + "; failed: " + String.join("; ", failed));
    }
    return installed;
  }

  static void requireWithinCap(int selected, int cap) {
    if (selected > cap) {
      throw new IllegalArgumentException(selected + " skills selected, but at most " + cap
          + " are installed per call. Select fewer, or raise qlawkus.skill-hub.max-installs-per-call.");
    }
  }
}
