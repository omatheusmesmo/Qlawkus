package dev.omatheusmesmo.qlawkus.tools.skillhub;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Runtime configuration for the remote skill registry (SkillHub). Owned by the optional
 * {@code qlawkus-skill-hub} extension; absent from a distribution that does not ship it.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.skill-hub")
public interface SkillHubConfig {

  /**
   * Whether the remote skill-hub tools ({@code searchSkillHub}, {@code installSkill}) are active.
   * When {@code false}, both tools refuse and nothing reaches the network.
   */
  @WithDefault("true")
  boolean enabled();

  /**
   * Base URL of the remote skill registry. Search queries hit {@code <base-url>/api/search}.
   */
  @WithDefault("https://skills.sh")
  String baseUrl();

  /**
   * Install approval mode. {@code hitl} requires the owner to confirm before a fetched skill is
   * written to the owned root (the tool previews first and installs only on a confirmed re-call);
   * {@code yolo} installs immediately. Defaults to {@code hitl}.
   */
  @WithDefault("hitl")
  ApprovalMode approvalMode();

  /**
   * Maximum number of search hits returned to the agent in one call.
   */
  @WithDefault("10")
  int maxResults();

  /**
   * Timeout for a single HTTP request to the registry (search or fetch).
   */
  @WithDefault("10s")
  Duration requestTimeout();

  /**
   * Extra hosts (or base URLs) that serve a {@code /.well-known/agent-skills/index.json} index.
   * Each is consulted on search in addition to the registry, and its skills become installable by
   * their direct SKILL.md URL. Empty by default - search uses only the registry {@code base-url}.
   */
  Optional<List<String>> wellKnownHosts();

  /**
   * Publishing: stage the agent's own skills for sharing.
   */
  Publish publish();

  /**
   * How {@code installSkill} obtains the owner's go-ahead before writing a fetched skill.
   */
  enum ApprovalMode {
    /** Install immediately, no confirmation. */
    YOLO,
    /** Require explicit owner confirmation before writing. */
    HITL
  }

  interface Publish {

    /**
     * Whether {@code publishSkill} is active. Opt-in (disabled by default): publishing is an
     * outward-facing share action.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Directory where {@code publishSkill} writes the rendered SKILL.md (under
     * {@code <dir>/<name>/SKILL.md}). In a container, mount this to a host path that is a git
     * working copy; the owner reviews and pushes from the host. The hub never runs {@code git}.
     */
    @WithDefault("${user.home}/.qlawkus/published")
    String dir();
  }
}
