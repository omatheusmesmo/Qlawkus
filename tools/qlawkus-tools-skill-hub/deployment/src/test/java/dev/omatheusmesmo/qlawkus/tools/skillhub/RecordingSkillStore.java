package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test double for {@link SkillStore}: records saved skills and their origins in memory; the rest are no-ops. */
class RecordingSkillStore implements SkillStore {

  final Map<String, Skill> saved = new LinkedHashMap<>();
  final Map<String, SkillOrigin> origins = new LinkedHashMap<>();
  final Map<String, Map<String, byte[]>> scripts = new LinkedHashMap<>();
  final Map<String, String> scriptSources = new LinkedHashMap<>();
  boolean failScripts;

  @Override
  public List<SkillSummary> index() {
    return saved.values().stream().map(s -> new SkillSummary(s.name(), s.description())).toList();
  }

  @Override
  public Optional<Skill> get(String name) {
    return Optional.ofNullable(saved.get(name));
  }

  @Override
  public Skill save(Skill skill) {
    saved.put(skill.name(), skill);
    origins.remove(skill.name());
    return skill;
  }

  @Override
  public boolean delete(String name) {
    origins.remove(name);
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

  @Override
  public void verifyWritable() {
  }

  @Override
  public boolean saveScripts(String name, Map<String, byte[]> files, String source) {
    if (failScripts) {
      throw new IllegalStateException("disk full");
    }
    scripts.put(name, files);
    scriptSources.put(name, source);
    return true;
  }

  @Override
  public void recordOrigin(String name, SkillOrigin origin) {
    origins.put(name, origin);
  }

  @Override
  public Optional<SkillOrigin> originOf(String name) {
    return Optional.ofNullable(origins.get(name));
  }
}
