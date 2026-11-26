package dev.omatheusmesmo.qlawkus.skill;

import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.omatheusmesmo.qlawkus.store.markdown.WriteProbe;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
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
  private static final String SCRIPTS_DIR = "scripts";
  private static final String USAGE_FILE = ".qlawkus-usage.json";

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
    usage.put(skill.name(), current.withState(SkillState.ACTIVE).withOrigin(null));
    writeUsage(usage);
    return skill;
  }

  public void recordOrigin(String name, SkillOrigin origin) {
    if (!loadAll().containsKey(name)) {
      return;
    }
    Map<String, SkillUsage> usage = readUsage();
    SkillUsage current = usage.getOrDefault(name, SkillUsage.initial());
    usage.put(name, current.withOrigin(origin));
    writeUsage(usage);
  }

  public Optional<SkillOrigin> originOf(String name) {
    return Optional.ofNullable(readUsage().get(name)).map(SkillUsage::origin);
  }

  public Optional<SkillScripts> scriptsOf(String name) {
    Loaded loaded = loadAll().get(name);
    if (loaded == null) {
      return Optional.empty();
    }
    Path dir = loaded.file().getParent().resolve(SCRIPTS_DIR);
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    boolean installed = Files.isRegularFile(dir.resolve(SkillScripts.INSTALLED_MARKER), LinkOption.NOFOLLOW_LINKS);
    return Optional.of(new SkillScripts(dir, installed));
  }

  public boolean saveScripts(String name, Map<String, byte[]> scripts, String source) {
    Path scriptsDir = ownedRoot().resolve(sanitize(name)).resolve(SCRIPTS_DIR);
    Map<Path, byte[]> targets = new LinkedHashMap<>();
    for (Map.Entry<String, byte[]> script : scripts.entrySet()) {
      targets.put(scriptTarget(scriptsDir, script.getKey()), script.getValue());
    }
    try {
      deleteTree(scriptsDir);
      if (targets.isEmpty()) {
        return true;
      }
      Files.createDirectories(scriptsDir);
      Files.writeString(scriptsDir.resolve(SkillScripts.INSTALLED_MARKER), source + "\n", StandardCharsets.UTF_8);
      for (Map.Entry<Path, byte[]> target : targets.entrySet()) {
        Files.createDirectories(target.getKey().getParent());
        Files.write(target.getKey(), target.getValue());
        makeExecutable(target.getKey());
      }
      Log.infof("Skill scripts saved: %s (%d files)", name, targets.size());
      return true;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save scripts of skill " + name, e);
    }
  }

  private static Path scriptTarget(Path scriptsDir, String relative) {
    if (relative == null || relative.isBlank() || relative.startsWith("/") || relative.contains("\\")
        || relative.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Invalid script path: " + relative);
    }
    Path target = scriptsDir.resolve(relative).normalize();
    if (!target.startsWith(scriptsDir) || target.equals(scriptsDir)
        || scriptsDir.relativize(target).toString().equals(SkillScripts.INSTALLED_MARKER)) {
      throw new IllegalArgumentException("Invalid script path: " + relative);
    }
    return target;
  }

  private static void makeExecutable(Path file) throws IOException {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-x---"));
    } catch (UnsupportedOperationException e) {
      Log.debugf("No POSIX permissions on %s", file);
    }
  }

  private static void deleteTree(Path dir) throws IOException {
    if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public boolean delete(String name) {
    Path dir = ownedRoot().resolve(sanitize(name));
    try {
      boolean removed = Files.deleteIfExists(dir.resolve(SKILL_FILE));
      if (removed) {
        deleteTree(dir.resolve(SCRIPTS_DIR));
      }
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
      FileSystemSkill parsed = FileSystemSkillLoader.loadSkill(dir);
      String name = isBlank(parsed.name()) ? dir.getFileName().toString() : parsed.name();
      String description = parsed.description() == null ? "" : parsed.description();
      byName.putIfAbsent(name, new Loaded(new Skill(name, description, parsed.content()), file));
    } catch (RuntimeException e) {
      Log.warnf(e, "Failed to read skill at %s", file);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
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
      return SkillUsageJson.read(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException e) {
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
      Files.writeString(usageFile(), SkillUsageJson.write(usage), StandardCharsets.UTF_8);
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

  public void verifyWritable() {
    WriteProbe.verify(ownedRoot());
  }
}
