package dev.omatheusmesmo.qlawkus.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * SKILL.md file operations over an ordered list of roots: each skill is a directory
 * {@code <root>/<name>/SKILL.md}. All roots are read (first occurrence of a name wins); writes go
 * to the first root only (the owned, writable one). Usage telemetry and lifecycle state live in a
 * sidecar {@code .qlawkus-usage.json} in the owned root, keeping the SKILL.md content clean and
 * portable. Reused by {@link MarkdownSkillStore} and the hybrid backend.
 */
public class MarkdownSkillFiles {

  private static final String SKILL_FILE = "SKILL.md";
  private static final String USAGE_FILE = ".qlawkus-usage.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final List<Path> roots;

  public MarkdownSkillFiles(List<Path> roots) {
    this.roots = List.copyOf(roots);
  }

  private record Loaded(Skill skill, Path file) {
  }

  public List<SkillSummary> index() {
    Map<String, SkillUsage> usage = readUsage();
    List<SkillSummary> summaries = new ArrayList<>();
    for (Loaded loaded : loadAll().values()) {
      SkillUsage entry = usage.get(loaded.skill().name());
      if (entry != null && entry.state() == SkillState.ARCHIVED) {
        continue;
      }
      summaries.add(new SkillSummary(loaded.skill().name(), loaded.skill().description()));
    }
    return summaries;
  }

  public Optional<Skill> get(String name) {
    Loaded loaded = loadAll().get(name);
    return loaded == null ? Optional.empty() : Optional.of(loaded.skill());
  }

  public Skill save(Skill skill) {
    if (skill.name() == null || skill.name().isBlank()) {
      throw new IllegalArgumentException("Skill name must not be blank");
    }
    String name = sanitize(skill.name());
    Path dir = ownedRoot().resolve(name);
    try {
      Files.createDirectories(dir);
      Files.writeString(dir.resolve(SKILL_FILE), SkillFrontmatter.render(skill),
          StandardCharsets.UTF_8);
      Log.infof("Skill saved: %s", skill.name());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save skill " + skill.name(), e);
    }
    Map<String, SkillUsage> usage = readUsage();
    SkillUsage current = usage.getOrDefault(skill.name(), SkillUsage.initial());
    usage.put(skill.name(), current.withState(SkillState.ACTIVE));
    writeUsage(usage);
    return skill;
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
      if (removed) {
        Map<String, SkillUsage> usage = readUsage();
        if (usage.remove(name) != null) {
          writeUsage(usage);
        }
      }
      return removed;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete skill " + name, e);
    }
  }

  public void recordUse(String name) {
    if (!loadAll().containsKey(name)) {
      return;
    }
    Map<String, SkillUsage> usage = readUsage();
    SkillUsage current = usage.getOrDefault(name, SkillUsage.initial());
    usage.put(name, current.usedAt(System.currentTimeMillis()));
    writeUsage(usage);
  }

  public boolean setPinned(String name, boolean pinned) {
    if (!loadAll().containsKey(name)) {
      return false;
    }
    Map<String, SkillUsage> usage = readUsage();
    SkillUsage current = usage.getOrDefault(name, SkillUsage.initial());
    usage.put(name, current.withPinned(pinned));
    writeUsage(usage);
    return true;
  }

  public int sweepLifecycle(int staleAfterDays, int archiveAfterDays) {
    Map<String, Loaded> skills = loadAll();
    Map<String, SkillUsage> usage = readUsage();
    long now = System.currentTimeMillis();
    int changed = 0;
    for (Loaded loaded : skills.values()) {
      String name = loaded.skill().name();
      SkillUsage current = usage.getOrDefault(name, SkillUsage.initial());
      if (current.pinned()) {
        continue;
      }
      long reference = current.lastUsedEpochMs() > 0
          ? current.lastUsedEpochMs()
          : fileModified(loaded.file(), now);
      long ageDays = Duration.ofMillis(Math.max(0, now - reference)).toDays();
      SkillState target = ageDays >= archiveAfterDays ? SkillState.ARCHIVED
          : ageDays >= staleAfterDays ? SkillState.STALE
          : SkillState.ACTIVE;
      if (target != current.state()) {
        usage.put(name, current.withState(target));
        changed++;
      }
    }
    if (changed > 0) {
      writeUsage(usage);
    }
    return changed;
  }

  private Map<String, Loaded> loadAll() {
    Map<String, Loaded> byName = new LinkedHashMap<>();
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

  private void readSkill(Path dir, Map<String, Loaded> byName) {
    Path file = dir.resolve(SKILL_FILE);
    if (!Files.isRegularFile(file)) {
      return;
    }
    try {
      SkillFrontmatter.Parsed parsed =
          SkillFrontmatter.parse(Files.readString(file, StandardCharsets.UTF_8));
      String name = parsed.name().orElse(dir.getFileName().toString());
      byName.putIfAbsent(name,
          new Loaded(new Skill(name, parsed.description().orElse(""), parsed.body()), file));
    } catch (IOException e) {
      Log.warnf(e, "Failed to read skill at %s", file);
    }
  }

  private Map<String, SkillUsage> readUsage() {
    if (roots.isEmpty()) {
      return new LinkedHashMap<>();
    }
    Path file = usageFile();
    if (!Files.isRegularFile(file)) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, SkillUsage> parsed = MAPPER.readValue(
          Files.readString(file, StandardCharsets.UTF_8),
          new TypeReference<LinkedHashMap<String, SkillUsage>>() {});
      return parsed == null ? new LinkedHashMap<>() : parsed;
    } catch (IOException e) {
      Log.warnf(e, "Failed to read skill usage sidecar %s", file);
      return new LinkedHashMap<>();
    }
  }

  private void writeUsage(Map<String, SkillUsage> usage) {
    if (roots.isEmpty()) {
      return;
    }
    try {
      Files.createDirectories(ownedRoot());
      Files.writeString(usageFile(), MAPPER.writeValueAsString(usage), StandardCharsets.UTF_8);
    } catch (IOException e) {
      Log.warnf(e, "Failed to write skill usage sidecar");
    }
  }

  private Path usageFile() {
    return ownedRoot().resolve(USAGE_FILE);
  }

  private Path ownedRoot() {
    if (roots.isEmpty()) {
      throw new IllegalStateException("No skill roots configured (qlawkus.skills.roots)");
    }
    return roots.get(0);
  }

  private static long fileModified(Path file, long fallback) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException e) {
      return fallback;
    }
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
