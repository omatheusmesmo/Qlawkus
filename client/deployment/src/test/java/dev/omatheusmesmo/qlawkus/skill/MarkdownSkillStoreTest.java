package dev.omatheusmesmo.qlawkus.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownSkillStoreTest {

  @TempDir
  Path tempDir;

  private MarkdownSkillStore storeWithRoots(Path... roots) {
    List<String> rootStrings = Arrays.stream(roots).map(Path::toString).toList();
    SkillsConfig config = new SkillsConfig() {
      @Override
      public boolean enabled() {
        return true;
      }

      @Override
      public List<String> roots() {
        return rootStrings;
      }
    };
    return new MarkdownSkillStore(config);
  }

  @Test
  void saveAndGet_roundTrip() {
    MarkdownSkillStore store = storeWithRoots(tempDir);
    store.save(new Skill("triage-inbox", "Triage the owner's inbox",
        "1. Open inbox\n2. Sort by sender"));

    Optional<Skill> loaded = store.get("triage-inbox");
    assertTrue(loaded.isPresent());
    assertEquals("Triage the owner's inbox", loaded.get().description());
    assertTrue(loaded.get().body().contains("Sort by sender"));
  }

  @Test
  void index_listsNameAndDescription() {
    MarkdownSkillStore store = storeWithRoots(tempDir);
    store.save(new Skill("post-weekly-update", "Post the weekly status update", "do it"));

    List<SkillSummary> index = store.index();
    assertEquals(1, index.size());
    assertEquals("post-weekly-update", index.get(0).name());
    assertEquals("Post the weekly status update", index.get(0).description());
  }

  @Test
  void get_unknownSkill_returnsEmpty() {
    assertTrue(storeWithRoots(tempDir).get("nope").isEmpty());
  }

  @Test
  void delete_removesSkill() {
    MarkdownSkillStore store = storeWithRoots(tempDir);
    store.save(new Skill("temp", "temporary", "body"));

    assertTrue(store.delete("temp"));
    assertTrue(store.get("temp").isEmpty());
    assertFalse(store.delete("temp"));
  }

  @Test
  void reads_handWrittenSkillFile() throws IOException {
    Path dir = Files.createDirectories(tempDir.resolve("web-design"));
    Files.writeString(dir.resolve("SKILL.md"),
        "---\nname: web-design\ndescription: \"Web design guidelines\"\n---\n\n"
            + "# Web Design\nUse a grid.\n",
        StandardCharsets.UTF_8);

    Skill skill = storeWithRoots(tempDir).get("web-design").orElseThrow();
    assertEquals("Web design guidelines", skill.description());
    assertTrue(skill.body().contains("Use a grid."));
  }

  @Test
  void firstRoot_winsOnNameClash() throws IOException {
    Path owned = Files.createDirectories(tempDir.resolve("owned"));
    Path shared = Files.createDirectories(tempDir.resolve("shared"));
    writeSkill(shared, "dup", "from shared");
    storeWithRoots(owned).save(new Skill("dup", "from owned", "body"));

    Skill resolved = storeWithRoots(owned, shared).get("dup").orElseThrow();
    assertEquals("from owned", resolved.description());
  }

  @Test
  void save_rejectsPathTraversalName() {
    MarkdownSkillStore store = storeWithRoots(tempDir);
    assertThrows(IllegalArgumentException.class,
        () -> store.save(new Skill("../evil", "x", "y")));
  }

  private void writeSkill(Path root, String name, String description) throws IOException {
    Path dir = Files.createDirectories(root.resolve(name));
    Files.writeString(dir.resolve("SKILL.md"),
        "---\nname: " + name + "\ndescription: \"" + description + "\"\n---\n\nbody\n",
        StandardCharsets.UTF_8);
  }
}
