package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link SkillStore} test double for unit testing skill tools without files or a DB. */
class InMemorySkillStore implements SkillStore {

  private final Map<String, Skill> skills = new LinkedHashMap<>();

  @Override
  public List<SkillSummary> index() {
    return skills.values().stream()
        .map(skill -> new SkillSummary(skill.name(), skill.description()))
        .toList();
  }

  @Override
  public Optional<Skill> get(String name) {
    return Optional.ofNullable(skills.get(name));
  }

  @Override
  public Skill save(Skill skill) {
    skills.put(skill.name(), skill);
    return skill;
  }

  @Override
  public boolean delete(String name) {
    return skills.remove(name) != null;
  }
}
