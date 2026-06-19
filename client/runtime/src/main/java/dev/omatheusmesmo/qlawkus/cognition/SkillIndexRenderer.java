package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.List;

/**
 * Renders the skill index injected into the system prompt every turn: just each skill's name and
 * description (progressive disclosure). The model loads a full body on demand via the
 * {@code viewSkill} tool. Kept separate from {@link SoulEngine} so it can be unit tested without
 * booting the agent.
 */
public final class SkillIndexRenderer {

  private SkillIndexRenderer() {
  }

  public static String render(List<SkillSummary> skills, int max) {
    if (skills == null || skills.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("## Skills\n\n");
    sb.append("These are reusable procedures you have learned. When a task matches one, call ")
        .append("`viewSkill` with its exact name to load the full instructions before acting. ")
        .append("When you work out a repeatable way to do something, save it with ")
        .append("`createOrUpdateSkill` so you can reuse it later.\n\n");

    int limit = Math.min(skills.size(), Math.max(0, max));
    for (int i = 0; i < limit; i++) {
      SkillSummary skill = skills.get(i);
      sb.append("- **").append(skill.name()).append("**");
      if (skill.description() != null && !skill.description().isBlank()) {
        sb.append(": ").append(skill.description());
      }
      sb.append('\n');
    }
    if (skills.size() > limit) {
      sb.append("\n_(").append(skills.size() - limit)
          .append(" more skills not shown; ask to list all skills if needed.)_\n");
    }
    return sb.toString();
  }
}
