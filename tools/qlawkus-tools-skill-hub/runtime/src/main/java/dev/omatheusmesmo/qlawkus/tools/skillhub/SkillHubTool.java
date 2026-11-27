package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.approval.ApprovedExecution;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.skill.SkillScripts;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lets the agent reach a remote skill registry mid-conversation: discover skills others published,
 * install them into its own procedural memory, update them from where they came from, and publish its
 * own. Installed skills land in the owned root, so they enter the injected skills index on the next
 * turn (no restart).
 *
 * <p>Every writing tool honors {@link SkillHubConfig#approvalMode()}: in {@code yolo} it writes
 * immediately; in {@code hitl} the platform's durable approval gate holds the call until the owner
 * approves it ({@link SkillHubApprovalRequirement}), and the tool itself writes only inside that approved
 * execution. The {@code confirm} parameter is kept so existing tool schemas stay valid, but it is
 * ignored: a flag the model sets itself is not an approval. Publish writes to the configured directory
 * only (never {@code git push}); the owner pushes from the host.
 */
@QlawTool
@ApplicationScoped
public class SkillHubTool {

  private static final String NOT_APPROVED =
      "Nothing was written: this change needs the owner's approval, which the platform asks for. Do not "
          + "retry and do not pass confirm; tell the owner it is waiting.";

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
      return "Found " + hits.size() + " skill(s):\n" + formatHits(hits);
    } catch (RuntimeException e) {
      Log.warnf(e, "searchSkillHub failed");
      Log.debugf("searchSkillHub query: '%s'", query);
      return "Skill search failed: " + e.getMessage();
    }
  }

  /**
   * Installs the one skill a source names, or the best match for a plain skill name. A failed install
   * throws instead of returning text: the tool framework turns the exception into an error result for
   * the model, and {@code MeteredToolExecutor} counts it as {@code outcome=error}.
   */
  @Tool("""
      Install one skill from a source: a skill name to search for, an owner/repo or owner/repo/skill \
      slug, or a link (a SKILL.md, GitHub, GitLab, Bitbucket or gist link, or a single-skill folder). \
      A plain name is searched for: one clear match is installed, several are listed so you can ask the \
      owner which one. For a repository, folder or archive that holds several skills, use \
      listSkillsInSource and installSkillsFromSource instead. In HITL approval mode the \
      call is held until the owner approves it in chat; tell them it is waiting and do not retry. In YOLO \
      mode it installs immediately.""")
  public String installSkill(
      @P("A skill name, or the source (slug or link) to install") String source,
      @P("Ignored: approval is requested from the owner by the platform") boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (source == null || source.isBlank()) {
      return "Provide the skill source to install (a name, or a slug or link from searchSkillHub).";
    }
    SkillSource parsed = SkillSourceParser.parse(source);
    if (parsed instanceof NameSource name) {
      return installByName(name.name(), confirm);
    }
    return installLocation(source.strip(), confirm);
  }

  @Tool("""
      List the skills a repository folder, a repository, an archive or a gist holds, without installing \
      any. Use it before installSkillsFromSource to show the owner what is there.""")
  public String listSkillsInSource(
      @P("The repository, folder, archive or gist link or slug to look inside") String source) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (source == null || source.isBlank()) {
      return "Provide the repository, folder, archive or gist to look inside.";
    }
    List<SkillCandidate> found = skillHub.discover(source.strip());
    return "Found " + found.size() + " skill(s) in " + source.strip() + ":\n"
        + found.stream()
            .map(c -> "- " + c.name() + (c.location() == null ? "" : " (" + c.location() + ")")
                + (c.scripts().isEmpty() ? "" : " [" + c.scripts().size() + " script file(s)]"))
            .collect(Collectors.joining("\n"))
        + "\nInstall some or all with installSkillsFromSource.";
  }

  @Tool("""
      Install several skills from one source (a repository, a folder, an archive or a gist) under one \
      approval. Pass the skill names from listSkillsInSource comma-separated, or 'all'. A selection \
      larger than the configured per-call cap is refused before anything is written. In HITL mode the call \
      is held until the owner approves it in chat.""")
  public String installSkillsFromSource(
      @P("The repository, folder, archive or gist link or slug") String source,
      @P("Comma-separated skill names from listSkillsInSource, or 'all'") String skills,
      @P("Ignored: approval is requested from the owner by the platform") boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (source == null || source.isBlank()) {
      return "Provide the repository, folder, archive or gist to install from.";
    }
    if (skills == null || skills.isBlank()) {
      throw new IllegalArgumentException("Name the skills to install, comma-separated (from listSkillsInSource), "
          + "or pass 'all'.");
    }
    String trimmed = source.strip();
    List<SkillCandidate> selected = SkillSelection.select(skillHub.discover(trimmed), skills, trimmed);
    SkillSelection.requireWithinCap(selected.size(), config.maxInstallsPerCall());
    String names = selected.stream().map(SkillCandidate::name).collect(Collectors.joining(", "));
    if (awaitsApproval()) {
      return "Installing " + selected.size() + " skill(s) from " + trimmed + ": " + names
          + ". " + NOT_APPROVED;
    }
    List<String> installed = SkillSelection.installAll(skillHub, selected);
    String withScripts = installed.stream()
        .filter(name -> skillStore.scriptsOf(name).map(SkillScripts::installed).orElse(false))
        .collect(Collectors.joining(", "));
    return "Installed " + installed.size() + " skill(s): " + String.join(", ", installed)
        + (withScripts.isEmpty() ? "" : ". Carrying scripts: " + withScripts + ". Nothing ran, and each run goes "
            + "through runInstalledSkillScript, which waits for the owner's approval.");
  }

  @Tool("""
      Install a SKILL.md the owner pasted into the chat. The text must hold a frontmatter with a name. \
      The same validation and approval apply as installSkill: in HITL mode the call is held \
      until the owner approves it in chat.""")
  public String installSkillFromText(
      @P("The full SKILL.md text, frontmatter included") String text,
      @P("Ignored: approval is requested from the owner by the platform") boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (text == null || text.isBlank()) {
      return "Paste the SKILL.md text to install.";
    }
    Skill skill = SkillMarkdown.parsePasted(text, config.maxSkillBytes());
    if (awaitsApproval()) {
      return "Installing skill '" + skill.name() + "' (" + skill.description() + ") from the pasted text will "
          + "write it to your skills. " + NOT_APPROVED;
    }
    skillStore.save(skill);
    return "Installed skill: " + skill.name();
  }

  @Tool("""
      Update an installed skill from the source it was installed from, picking up the current version. \
      Skills written by you or pasted in have no recorded source and cannot be updated this way. In HITL \
      mode the call is held until the owner approves it in chat.""")
  public String updateSkill(
      @P("The exact name of an installed skill") String name,
      @P("Ignored: approval is requested from the owner by the platform") boolean confirm) {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    if (name == null || name.isBlank()) {
      return "Provide the exact name of the skill to update.";
    }
    String skillName = name.strip();
    SkillOrigin recorded = SkillUpdates.recordedOrigin(skillStore, skillName);
    if (awaitsApproval()) {
      return "Updating '" + skillName + "' from " + recorded.source() + " will overwrite its SKILL.md with the "
          + "current version there. " + NOT_APPROVED;
    }
    Skill updated = SkillUpdates.refresh(skillHub, skillStore, skillName);
    return "Updated skill: " + updated.name() + " from " + recorded.source();
  }

  @Tool("""
      Show where each installed skill came from (its install source and when it was installed), so the \
      owner can see what to update or remove. Skills with no recorded source were pasted or written by you.""")
  public String listSkillSources() {
    if (!config.enabled()) {
      return "The skill hub is disabled (qlawkus.skill-hub.enabled=false).";
    }
    List<SkillSummary> index = skillStore.index();
    if (index.isEmpty()) {
      return "No skills in your procedural memory.";
    }
    return index.stream()
        .map(summary -> "- " + summary.name() + " - " + describeOrigin(skillStore.originOf(summary.name())))
        .collect(Collectors.joining("\n"));
  }

  @ToolAccess(AccessClass.CREATE)
  @Tool("""
      Publish one of your own skills so it can be shared: renders it to a SKILL.md under the \
      configured publish directory. This does NOT push to any registry - the owner reviews the file \
      and pushes it from the host. Honors the same HITL/YOLO approval mode as install.""")
  public String publishSkill(
      @P("The exact name of a skill in your procedural memory to publish") String name,
      @P("Ignored: approval is requested from the owner by the platform") boolean confirm) {
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
    if (awaitsApproval()) {
      return "Publishing '" + name + "' will write its SKILL.md to " + config.publish().dir()
          + ". " + NOT_APPROVED;
    }
    try {
      Path written = skillHub.publish(skill.get());
      return "Published skill '" + name + "' to " + written + ". Review and push it from the host.";
    } catch (RuntimeException e) {
      Log.warnf(e, "publishSkill failed for '%s'", name);
      return "Publish failed for '" + name + "': " + e.getMessage();
    }
  }

  private boolean awaitsApproval() {
    return config.approvalMode() == ApprovalMode.HITL && !ApprovedExecution.active();
  }

  private String installByName(String name, boolean confirm) {
    List<SkillRef> hits = skillHub.search(name, config.maxResults());
    if (hits.isEmpty()) {
      throw new IllegalStateException("No skill named '" + name + "' in the registry or on the well-known hosts. "
          + "Try searchSkillHub with other words, or paste a link to the skill's SKILL.md.");
    }
    Optional<SkillRef> chosen = clearMatch(hits, name);
    if (chosen.isEmpty()) {
      return "Several skills match '" + name + "'. Ask the owner which one, then call installSkill with that "
          + "skill's source:\n" + formatHits(hits);
    }
    return installLocation(chosen.get().source(), confirm);
  }

  /** The only hit, or the one hit whose name is exactly the query; several other matches are not clear. */
  private static Optional<SkillRef> clearMatch(List<SkillRef> hits, String name) {
    if (hits.size() == 1) {
      return Optional.of(hits.get(0));
    }
    List<SkillRef> exact = hits.stream()
        .filter(hit -> hit.name() != null && hit.name().equalsIgnoreCase(name))
        .toList();
    return exact.size() == 1 ? Optional.of(exact.get(0)) : Optional.empty();
  }

  private String installLocation(String source, boolean confirm) {
    if (awaitsApproval()) {
      return "Installing '" + source + "' will download a SKILL.md, and any scripts it carries, from the "
          + "internet and add them to your skills. " + NOT_APPROVED;
    }
    Skill saved = skillHub.install(source);
    return "Installed skill: " + saved.name() + scriptNote(saved.name());
  }

  /**
   * Says that an installed skill carries scripts and how they run, so the owner and the model both know
   * the install did not run anything and that running one waits for the owner.
   */
  private String scriptNote(String skill) {
    boolean installedScripts = skillStore.scriptsOf(skill).map(SkillScripts::installed).orElse(false);
    return installedScripts
        ? ". It carries scripts: nothing ran, and each run goes through runInstalledSkillScript, which waits "
            + "for the owner's approval."
        : "";
  }

  private static String formatHits(List<SkillRef> hits) {
    return hits.stream()
        .map(h -> "- " + h.name() + " (source: " + h.source() + ") - " + h.description())
        .collect(Collectors.joining("\n"));
  }

  private static String describeOrigin(Optional<SkillOrigin> origin) {
    if (origin.isEmpty()) {
      return "no recorded source (pasted or written by you)";
    }
    SkillOrigin recorded = origin.get();
    String folder = recorded.candidate() == null ? "" : " (skill '" + recorded.candidate() + "')";
    return "from " + recorded.source() + folder + ", installed " + Instant.ofEpochMilli(recorded.installedEpochMs());
  }
}
