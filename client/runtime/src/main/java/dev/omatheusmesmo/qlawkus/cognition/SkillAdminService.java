package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Administrative operations over the skill store, backing the {@code /api/admin/skills} endpoints.
 */
@ApplicationScoped
public class SkillAdminService {

  @Inject
  SkillStore skillStore;

  public List<SkillSummary> listSkills() {
    return skillStore.index();
  }

  public Optional<Skill> getSkill(String name) {
    return skillStore.get(name);
  }

  public boolean deleteSkill(String name) {
    return skillStore.delete(name);
  }
}
