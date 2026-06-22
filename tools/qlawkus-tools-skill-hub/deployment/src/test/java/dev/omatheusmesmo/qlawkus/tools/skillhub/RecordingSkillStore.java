package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test double for {@link SkillStore}: records saved skills in memory; the rest are no-ops. */
class RecordingSkillStore implements SkillStore {

  final Map<String, Skill> saved = new LinkedHashMap<>();

  @Override
  public List<SkillSummary> index() {
    return List.of();
  }

  @Override
  public Optional<Skill> get(String name) {
    return Optional.ofNullable(saved.get(name));
  }

  @Override
  public Skill save(Skill skill) {
    saved.put(skill.name(), skill);
    return skill;
  }

  @Override
  public boolean delete(String name) {
    return saved.remove(name) != null;
  }

  @Override
  public void recordUse(String name) {
  }

  @Override
  public boolean setPinned(String name, boolean pinned) {
    return saved.containsKey(name);
  }

  @Override
  public int sweepLifecycle(int staleAfterDays, int archiveAfterDays) {
    return 0;
  }
}
