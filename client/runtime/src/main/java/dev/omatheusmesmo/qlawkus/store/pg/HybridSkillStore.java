package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.BundledSkills;
import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillFiles;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillSummary;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Hybrid {@link SkillStore}, active when {@code qlawkus.cognition.backend=hybrid}. SKILL.md files
 * are the source of truth (reads and the canonical write); every write is also mirrored into the
 * {@code skill} table so the content is available to pgvector-backed ranking and search later.
 * Selected at build time via {@link IfBuildProperty}.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridSkillStore implements SkillStore {

  private final MarkdownSkillFiles files;
  private final BundledSkills bundled;

  @Inject
  public HybridSkillStore(SkillsConfig config, BundledSkills bundled) {
    this.files = new MarkdownSkillFiles(config.roots().stream().map(Path::of).toList());
    this.bundled = bundled;
  }

  @Override
  public List<SkillSummary> index() {
    return bundled.mergedIndex(files.index());
  }

  @Override
  public Optional<Skill> get(String name) {
    return files.get(name).or(() -> bundled.get(name));
  }

  @Override
  @Transactional
  public Skill save(Skill skill) {
    Skill saved = files.save(skill);
    SkillEntity.upsert(saved.name(),
        saved.description() == null ? "" : saved.description(),
        saved.body() == null ? "" : saved.body());
    return saved;
  }

  @Override
  @Transactional
  public boolean delete(String name) {
    boolean removed = files.delete(name);
    SkillEntity.deleteById(name);
    return removed;
  }
}
