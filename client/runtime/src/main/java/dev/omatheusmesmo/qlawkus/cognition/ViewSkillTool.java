package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads the full instructions of a skill on demand (progressive disclosure). The skills index
 * (name + description) is always in the system prompt; the body is fetched only when the agent
 * decides a skill applies.
 */
@ApplicationScoped
public class ViewSkillTool {

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
    return skill.body();
  }

  private void recordUse(String name) {
    try {
      skillStore.recordUse(name);
    } catch (RuntimeException e) {
      Log.debugf(e, "Failed to record use of skill %s", name);
    }
  }
}
