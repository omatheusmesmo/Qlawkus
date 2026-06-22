package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lets the agent reach a remote skill registry mid-conversation: discover skills others published,
 * install them into its own procedural memory, and publish its own. Installed skills land in the
 * owned root, so they enter the injected skills index on the next turn (no restart).
 *
 * <p>Install honors {@link SkillHubConfig#approvalMode()}: in {@code yolo} it writes immediately; in
 * {@code hitl} the first call only previews and the owner's explicit go-ahead is required before a
 * confirmed re-call actually writes. Publish writes to the configured directory only (never
 * {@code git push}); the owner pushes from the host.
 */
@QlawTool
@ApplicationScoped
public class SkillHubTool {

  private final SkillHub skillHub;
  private final SkillStore skillStore;
  private final SkillHubConfig config;

  @Inject
  public SkillHubTool(SkillHub skillHub, SkillStore skillStore, SkillHubConfig config) {
    this.skillHub = skillHub;
    this.skillStore = skillStore;
    this.config = config;
  }

  @Tool("""
      Search a remote skill registry for reusable skills (how-to procedures) published by others. \
      Use this when the owner wants a capability you do not yet have a skill for, before authoring \
      one from scratch. Returns matching skills with the 'source' you pass to installSkill.""")
  public String searchSkillHub(@P("What the skill should do, in a few words") String query) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (query == null || query.isBlank()) {
      return "Provide a search query describing the skill you need.";
    }
    try {
      List<SkillRef> hits = skillHub.search(query.strip(), config.maxResults());
      if (hits.isEmpty()) {
        return "No skills found for: " + query;
      }
      return "Found " + hits.size() + " skill(s):\n"
          + hits.stream()
              .map(h -> "- " + h.name() + " (source: " + h.source() + ") - " + h.description())
              .collect(Collectors.joining("\n"));
    } catch (RuntimeException e) {
      Log.warnf(e, "searchSkillHub failed for query '%s'", query);
      return "Skill search failed: " + e.getMessage();
    }
  }

  @Tool("""
      Install a skill from the remote registry into your procedural memory, identified by the \
      'source' from searchSkillHub (an owner/repo slug or a SKILL.md URL). In HITL approval mode you \
      MUST first call with confirm=false to preview, present it to the owner, get explicit approval, \
      then call again with confirm=true; without approval nothing is written. In YOLO mode it \
      installs immediately.""")
  public String installSkill(
      @P("The skill source (owner/repo slug or SKILL.md URL) from searchSkillHub") String source,
      @P("Set true only after the owner explicitly approved this install (HITL mode)")
          boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (source == null || source.isBlank()) {
      return "Provide the skill source to install (from searchSkillHub).";
    }
    if (config.approvalMode() == ApprovalMode.HITL && !confirm) {
      return "Installing '" + source + "' will download a SKILL.md from the internet and add it to "
          + "your skills. Ask the owner to approve, then call installSkill again with confirm=true.";
    }
    try {
      Skill saved = skillHub.install(source.strip());
      return "Installed skill: " + saved.name();
    } catch (RuntimeException e) {
      Log.warnf(e, "installSkill failed for source '%s'", source);
      return "Install failed for '" + source + "': " + e.getMessage();
    }
  }

  @Tool("""
      Publish one of your own skills so it can be shared: renders it to a SKILL.md under the \
      configured publish directory. This does NOT push to any registry - the owner reviews the file \
      and pushes it from the host. Honors the same HITL/YOLO approval mode as install.""")
  public String publishSkill(
      @P("The exact name of a skill in your procedural memory to publish") String name,
      @P("Set true only after the owner explicitly approved this publish (HITL mode)")
          boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (!config.publish().enabled()) {
      return "Publishing is disabled (qlawkus.skill-hub.publish.enabled=false).";
    }
    if (name == null || name.isBlank()) {
      return "Provide the exact skill name to publish.";
    }
    Optional<Skill> skill = skillStore.get(name.strip());
    if (skill.isEmpty()) {
      return "No skill named '" + name + "' was found in your procedural memory.";
    }
    if (config.approvalMode() == ApprovalMode.HITL && !confirm) {
      return "Publishing '" + name + "' will write its SKILL.md to " + config.publish().dir()
          + ". Ask the owner to approve, then call publishSkill again with confirm=true.";
    }
    try {
      Path written = skillHub.publish(skill.get());
      return "Published skill '" + name + "' to " + written + ". Review and push it from the host.";
    } catch (RuntimeException e) {
      Log.warnf(e, "publishSkill failed for '%s'", name);
      return "Publish failed for '" + name + "': " + e.getMessage();
    }
  }
}
