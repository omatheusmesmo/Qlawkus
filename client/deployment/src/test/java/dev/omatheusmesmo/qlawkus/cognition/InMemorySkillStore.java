package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillState;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory {@link SkillStore} test double for unit testing skill tools without files or a DB. */
class InMemorySkillStore implements SkillStore {

  private final Map<String, Skill> skills = new LinkedHashMap<>();
  private final Map<String, SkillState> states = new HashMap<>();
  private final Set<String> pinned = new HashSet<>();

  @Override
  public List<SkillSummary> index() {
    return skills.values().stream()
        .filter(skill -> states.getOrDefault(skill.name(), SkillState.ACTIVE) != SkillState.ARCHIVED)
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
    states.remove(name);
    pinned.remove(name);
    return skills.remove(name) != null;
  }

  @Override
  public void recordUse(String name) {
    if (skills.containsKey(name)) {
      states.put(name, SkillState.ACTIVE);
    }
  }

  @Override
  public boolean setPinned(String name, boolean pin) {
    if (!skills.containsKey(name)) {
      return false;
    }
    if (pin) {
      pinned.add(name);
    } else {
      pinned.remove(name);
    }
    return true;
  }

  @Override
  public int sweepLifecycle(int staleAfterDays, int archiveAfterDays) {
    int changed = 0;
    for (String name : skills.keySet()) {
      if (pinned.contains(name)) {
        continue;
      }
      if (states.put(name, SkillState.ARCHIVED) != SkillState.ARCHIVED) {
        changed++;
      }
    }
    return changed;
  }
}
