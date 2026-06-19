package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Postgres-backed {@link SkillStore}, active when {@code qlawkus.cognition.backend=pgvector} (the
 * default). Skills live in the {@code skill} table. Selected at build time via
 * {@link IfBuildProperty}, so when another backend is chosen this bean is not wired at all.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "pgvector", enableIfMissing = true)
public class PgSkillStore implements SkillStore {

  @Override
  @Transactional
  public List<SkillSummary> index() {
    return SkillEntity.<SkillEntity>listAll().stream()
        .map(entity -> new SkillSummary(entity.name, entity.description))
        .toList();
  }

  @Override
  @Transactional
  public Optional<Skill> get(String name) {
    SkillEntity entity = SkillEntity.findById(name);
    return entity == null ? Optional.empty()
        : Optional.of(new Skill(entity.name, entity.description, entity.body));
  }

  @Override
  @Transactional
  public Skill save(Skill skill) {
    if (skill.name() == null || skill.name().isBlank()) {
      throw new IllegalArgumentException("Skill name must not be blank");
    }
    SkillEntity.upsert(skill.name(),
        skill.description() == null ? "" : skill.description(),
        skill.body() == null ? "" : skill.body());
    return skill;
  }

  @Override
  @Transactional
  public boolean delete(String name) {
    return SkillEntity.deleteById(name);
  }
}
