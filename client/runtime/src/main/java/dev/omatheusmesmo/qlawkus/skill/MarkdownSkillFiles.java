package dev.omatheusmesmo.qlawkus.skill;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * SKILL.md file operations over an ordered list of roots: each skill is a directory
 * {@code <root>/<name>/SKILL.md}. All roots are read (first occurrence of a name wins); writes go
 * to the first root only (the owned, writable one), so self-authored skills never leak into
 * shared cross-agent directories. Reused by {@link MarkdownSkillStore} and the hybrid backend.
 */
public class MarkdownSkillFiles {

  private static final String SKILL_FILE = "SKILL.md";

  private final List<Path> roots;

  public MarkdownSkillFiles(List<Path> roots) {
    this.roots = List.copyOf(roots);
  }

  public List<SkillSummary> index() {
    List<SkillSummary> summaries = new ArrayList<>();
    for (Skill skill : loadAll().values()) {
      summaries.add(new SkillSummary(skill.name(), skill.description()));
    }
    return summaries;
  }

  public Optional<Skill> get(String name) {
    return Optional.ofNullable(loadAll().get(name));
  }

  public Skill save(Skill skill) {
    if (skill.name() == null || skill.name().isBlank()) {
      throw new IllegalArgumentException("Skill name must not be blank");
    }
    Path dir = ownedRoot().resolve(sanitize(skill.name()));
    try {
      Files.createDirectories(dir);
      Files.writeString(dir.resolve(SKILL_FILE), SkillFrontmatter.render(skill),
          StandardCharsets.UTF_8);
      Log.infof("Skill saved: %s", skill.name());
      return skill;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save skill " + skill.name(), e);
    }
  }

  public boolean delete(String name) {
    Path dir = ownedRoot().resolve(sanitize(name));
    try {
      boolean removed = Files.deleteIfExists(dir.resolve(SKILL_FILE));
      if (removed && Files.isDirectory(dir)) {
        try (Stream<Path> remaining = Files.list(dir)) {
          if (remaining.findAny().isEmpty()) {
            Files.deleteIfExists(dir);
          }
        }
      }
      return removed;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete skill " + name, e);
    }
  }

  private Map<String, Skill> loadAll() {
    Map<String, Skill> byName = new LinkedHashMap<>();
    for (Path root : roots) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      try (Stream<Path> dirs = Files.list(root)) {
        dirs.filter(Files::isDirectory).forEach(dir -> readSkill(dir, byName));
      } catch (IOException e) {
        Log.warnf(e, "Failed to list skills root %s", root);
      }
    }
    return byName;
  }

  private void readSkill(Path dir, Map<String, Skill> byName) {
    Path file = dir.resolve(SKILL_FILE);
    if (!Files.isRegularFile(file)) {
      return;
    }
    try {
      SkillFrontmatter.Parsed parsed =
          SkillFrontmatter.parse(Files.readString(file, StandardCharsets.UTF_8));
      String name = parsed.name().orElse(dir.getFileName().toString());
      byName.putIfAbsent(name, new Skill(name, parsed.description().orElse(""), parsed.body()));
    } catch (IOException e) {
      Log.warnf(e, "Failed to read skill at %s", file);
    }
  }

  private Path ownedRoot() {
    if (roots.isEmpty()) {
      throw new IllegalStateException("No skill roots configured (qlawkus.skills.roots)");
    }
    return roots.get(0);
  }

  private static String sanitize(String name) {
    String trimmed = name.strip();
    if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\")
        || trimmed.contains("..")) {
      throw new IllegalArgumentException("Invalid skill name: " + name);
    }
    return trimmed;
  }
}
