package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import java.nio.file.Path;
import java.util.List;

/**
 * Client SPI for a remote skill registry (search + install + publish). Qlawkus does not host a hub;
 * it speaks to an external registry (skills.sh by default, the agentskills.io ecosystem) over HTTP,
 * and additionally discovers skills from any host serving a {@code /.well-known/agent-skills/}
 * index. This lives in the optional {@code qlawkus-tools-skill-hub} extension so a locked-down
 * distribution can omit the outward-facing capability entirely (module absent), not merely disable
 * it by config.
 *
 * <p>The default implementation is a {@code @DefaultBean} so it can be overridden without touching
 * callers.
 *
 * <p>Installed skills are written to the owned skills root via {@link SkillStore#save(Skill)}, so a
 * freshly installed skill enters the injected index on the next turn (no restart). Implementations
 * must confine writes to the owned root and reject path traversal in skill names.
 */
public interface SkillHub {

  /** Searches the registry, returning at most {@code limit} hits, best match first. */
  List<SkillRef> search(String query, int limit);

  /**
   * Fetches the one skill {@code source} names (a single SKILL.md link, an {@code owner/repo} slug, a
   * gist) and saves it to the owned root, recording the source as its origin. Throws when the source
   * is a name, holds several skills, cannot be resolved, or carries an unsafe skill name.
   */
  Skill install(String source);

  /**
   * Lists the skills a source holds without installing any: every SKILL.md under a repository folder,
   * every skill folder of an archive, or the single skill a link names. Throws when nothing is found.
   */
  List<SkillCandidate> discover(String source);

  /** Fetches (unless already read) and saves one candidate from {@link #discover(String)}. */
  Skill installCandidate(SkillCandidate candidate);

  /**
   * Renders {@code skill} to a SKILL.md and writes it into the configured publish directory (a path
   * the owner pushes from the host - the hub never runs {@code git push}), returning the written
   * file. Throws when the skill name is unsafe or the file cannot be written.
   */
  Path publish(Skill skill);
}
