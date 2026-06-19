package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Lets the agent author its own procedural memory: turn a proven approach into a reusable skill,
 * or remove one that no longer applies. Skills are saved to the owned, writable root. Mirrors
 * {@link UpdateUserProfileTool} (which maintains declarative owner facts); this maintains
 * actionable how-to knowledge.
 */
@ApplicationScoped
public class ManageSkillTool {

  private final SkillStore skillStore;

  @Inject
  public ManageSkillTool(SkillStore skillStore) {
    this.skillStore = skillStore;
  }

  @Tool("""
      Save a reusable skill (a how-to procedure) to your procedural memory, creating it or \
      replacing one with the same name. Use this when you work out a repeatable way to accomplish \
      a recurring task, so you can follow it again later instead of re-deriving it. Write the \
      instructions as concise, ordered steps. The name must be short and kebab-case (e.g. \
      "triage-inbox"); the description is one line shown in your skills list.""")
  public String createOrUpdateSkill(
      @P("Short kebab-case skill name, e.g. \"post-weekly-update\"") String name,
      @P("One-line description shown in the skills index") String description,
      @P("The full instructions as Markdown (ordered steps)") String instructions) {
    if (name == null || name.isBlank()) {
      return "A skill needs a non-empty name.";
    }
    try {
      Skill saved = skillStore.save(new Skill(name.strip(),
          description == null ? "" : description.strip(),
          instructions == null ? "" : instructions));
      return "Saved skill: " + saved.name();
    } catch (RuntimeException e) {
      Log.warnf(e, "ManageSkillTool: failed to save skill %s", name);
      return "Failed to save skill '" + name + "': " + e.getMessage();
    }
  }

  @Tool("Delete a skill from your procedural memory by its exact name.")
  public String deleteSkill(@P("The exact name of the skill to delete") String name) {
    if (name == null || name.isBlank()) {
      return "Provide the exact skill name to delete.";
    }
    try {
      return skillStore.delete(name.strip())
          ? "Deleted skill: " + name
          : "No skill named '" + name + "' was found.";
    } catch (RuntimeException e) {
      Log.warnf(e, "ManageSkillTool: failed to delete skill %s", name);
      return "Failed to delete skill '" + name + "': " + e.getMessage();
    }
  }
}
