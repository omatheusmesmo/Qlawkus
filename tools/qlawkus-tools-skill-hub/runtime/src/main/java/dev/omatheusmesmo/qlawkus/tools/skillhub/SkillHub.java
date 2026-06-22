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
   * Fetches the skill identified by {@code source} (an {@code owner/repo} slug or a direct URL to a
   * SKILL.md) and saves it to the owned root, returning the saved skill. Throws when the source
   * cannot be resolved, fetched, or carries an unsafe skill name.
   */
  Skill install(String source);

  /**
   * Renders {@code skill} to a SKILL.md and writes it into the configured publish directory (a path
   * the owner pushes from the host - the hub never runs {@code git push}), returning the written
   * file. Throws when the skill name is unsafe or the file cannot be written.
   */
  Path publish(Skill skill);
}
