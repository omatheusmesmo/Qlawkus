package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillScripts;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tool.shell.SkillScriptTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the full instructions of a skill on demand (progressive disclosure). The skills index
 * (name + description) is always in the system prompt; the body is fetched only when the agent
 * decides a skill applies.
 */
@ApplicationScoped
public class ViewSkillTool {

  private static final int MAX_SCRIPT_DEPTH = 3;
  private static final int MAX_LISTED_SCRIPTS = 20;

  private final SkillStore skillStore;

  @Inject
  public ViewSkillTool(SkillStore skillStore) {
    this.skillStore = skillStore;
  }

  @Tool("""
      Load the full step-by-step instructions of a skill by its exact name. Call this when a \
      skill listed in your Skills section matches the current task, before you act, so you follow \
      the proven procedure instead of improvising.""")
  public String viewSkill(@P("The exact name of the skill to load") String name) {
    if (name == null || name.isBlank()) {
      return "Provide the exact skill name.";
    }
    Skill skill = skillStore.get(name.strip()).orElse(null);
    if (skill == null || skill.body().isBlank()) {
      return "No skill named '" + name + "' was found. Check the Skills list for exact names.";
    }
    recordUse(skill.name());
    return skill.body() + scriptsNote(skill.name());
  }

  private String scriptsNote(String name) {
    Optional<SkillScripts> scripts = skillStore.scriptsOf(name);
    if (scripts.isEmpty()) {
      return "";
    }
    Path dir = scripts.get().dir();
    try (Stream<Path> files = Files.walk(dir, MAX_SCRIPT_DEPTH)) {
      String names = files.filter(Files::isRegularFile)
          .filter(file -> !file.getFileName().toString().equals(SkillScripts.INSTALLED_MARKER))
          .map(file -> dir.relativize(file).toString()).sorted().limit(MAX_LISTED_SCRIPTS)
          .collect(Collectors.joining(", "));
      if (names.isEmpty()) {
        return "";
      }
      String tool = scripts.get().installed() ? SkillScriptTool.INSTALLED_TOOL : SkillScriptTool.OWN_TOOL;
      return "\n\nScripts shipped with this skill, run with " + tool + " (skill = \"" + name
          + "\", script = the path below): " + names;
    } catch (IOException | RuntimeException e) {
      Log.warnf(e, "Failed to list the scripts of skill %s", name);
      return "";
    }
  }

  private void recordUse(String name) {
    try {
      skillStore.recordUse(name);
    } catch (RuntimeException e) {
      Log.debugf(e, "Failed to record use of skill %s", name);
    }
  }
}
