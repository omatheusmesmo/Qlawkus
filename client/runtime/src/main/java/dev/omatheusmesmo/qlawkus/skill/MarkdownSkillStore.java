package dev.omatheusmesmo.qlawkus.skill;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link SkillStore}: SKILL.md files on disk, no database. This is the Hermes/OpenClaw
 * model and the backend that lets qlawkus run with no database. Delegates to
 * {@link MarkdownSkillFiles}; a pgvector- or hybrid-backed implementation overrides it when
 * {@code qlawkus.cognition.backend} selects one.
 */
@ApplicationScoped
@DefaultBean
public class MarkdownSkillStore implements SkillStore {

  private final MarkdownSkillFiles files;
  private final BundledSkills bundled;

  @Inject
  public MarkdownSkillStore(SkillsConfig config, BundledSkills bundled) {
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
  public Skill save(Skill skill) {
    return files.save(skill);
  }

  @Override
  public boolean delete(String name) {
    return files.delete(name);
  }
}
