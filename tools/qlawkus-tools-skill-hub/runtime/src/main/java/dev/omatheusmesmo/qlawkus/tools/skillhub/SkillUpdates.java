package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import java.util.Optional;

/**
 * Re-fetches an installed skill from the source it was installed from. Shared by the agent's
 * {@code updateSkill} tool (which previews first in HITL mode) and the admin REST update endpoint (where
 * the request is the approval), so both refuse the same skills for the same reasons.
 */
public final class SkillUpdates {

  /** Prefix of the source recorded for a skill installed from an uploaded file, which cannot be re-fetched. */
  public static final String UPLOAD_PREFIX = "upload:";

  private SkillUpdates() {
  }

  /** Whether {@code origin} names a source that can be fetched again: every source except an uploaded file. */
  public static boolean fetchable(SkillOrigin origin) {
    return !origin.source().startsWith(UPLOAD_PREFIX);
  }

  /**
   * The origin an update would fetch from.
   *
   * @throws IllegalArgumentException when the skill has no recorded source, or was uploaded and so cannot be
   *     fetched again
   */
  static SkillOrigin recordedOrigin(SkillStore skillStore, String name) {
    Optional<SkillOrigin> origin = skillStore.originOf(name);
    if (origin.isEmpty()) {
      throw new IllegalArgumentException("No recorded source for skill '" + name + "': it was pasted or "
          + "written by the agent, so there is nowhere to update it from. Install it from a source to record one.");
    }
    if (!fetchable(origin.get())) {
      throw new IllegalArgumentException("'" + name + "' was installed from an uploaded file, which cannot be fetched "
          + "again. Upload the newer file instead.");
    }
    return origin.get();
  }

  /**
   * Fetches the skill from its recorded source and saves it over the current one.
   *
   * @throws IllegalArgumentException as {@link #recordedOrigin(SkillStore, String)}
   * @throws IllegalStateException when the source no longer holds a skill of that name
   */
  static Skill refresh(SkillHub skillHub, SkillStore skillStore, String name) {
    SkillOrigin recorded = recordedOrigin(skillStore, name);
    if (recorded.candidate() == null) {
      return skillHub.install(recorded.source());
    }
    SkillCandidate candidate = skillHub.discover(recorded.source()).stream()
        .filter(c -> c.name().equals(recorded.candidate()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("'" + recorded.source() + "' no longer holds a skill named '"
            + recorded.candidate() + "', so '" + name + "' cannot be updated from it."));
    return skillHub.installCandidate(candidate);
  }
}
