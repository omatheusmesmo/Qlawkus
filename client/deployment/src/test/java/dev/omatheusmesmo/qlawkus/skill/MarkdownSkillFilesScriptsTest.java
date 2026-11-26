package dev.omatheusmesmo.qlawkus.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins how a skill's {@code scripts/} folder is stored and told apart: scripts written by the Skill Hub
 * carry a marker, scripts the owner placed by hand do not, and the marker survives the agent rewriting
 * the SKILL.md, because that is the line the owner's approval for installed scripts is drawn on.
 */
class MarkdownSkillFilesScriptsTest {

  @TempDir
  Path owned;

  @TempDir
  Path shared;

  private MarkdownSkillFiles files;

  @BeforeEach
  void setUp() {
    files = new MarkdownSkillFiles(List.of(owned, shared));
    files.save(new Skill("triage", "Triage issues", "Run the script."));
  }

  private static Map<String, byte[]> scripts(String... pathAndContent) {
    Map<String, byte[]> map = new LinkedHashMap<>();
    for (int i = 0; i < pathAndContent.length; i += 2) {
      map.put(pathAndContent[i], pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
    }
    return map;
  }

  @Test
  void installedScriptsAreWrittenExecutableAndMarkedWithTheirSource() throws IOException {
    boolean saved = files.saveScripts("triage", scripts("run.sh", "#!/bin/sh\necho hi\n", "lib/util.sh", "x"),
        "acme/skills");

    assertTrue(saved);
    SkillScripts found = files.scriptsOf("triage").orElseThrow();
    assertTrue(found.installed());
    assertEquals(owned.resolve("triage").resolve("scripts"), found.dir());
    assertEquals("#!/bin/sh\necho hi\n", Files.readString(found.dir().resolve("run.sh")));
    assertTrue(Files.readString(found.dir().resolve(SkillScripts.INSTALLED_MARKER)).contains("acme/skills"));
    assertTrue(Files.getPosixFilePermissions(found.dir().resolve("lib/util.sh"))
        .contains(PosixFilePermission.OWNER_EXECUTE));
  }

  @Test
  void aReinstallReplacesTheOldScriptsInsteadOfMergingWithThem() {
    files.saveScripts("triage", scripts("old.sh", "old"), "acme/skills");

    files.saveScripts("triage", scripts("new.sh", "new"), "acme/skills");

    Path dir = files.scriptsOf("triage").orElseThrow().dir();
    assertFalse(Files.exists(dir.resolve("old.sh")));
    assertTrue(Files.exists(dir.resolve("new.sh")));
  }

  @Test
  void aScriptNamedToEscapeTheFolderIsRefusedAndWritesNothing() {
    assertThrows(IllegalArgumentException.class,
        () -> files.saveScripts("triage", scripts("../../escaped.sh", "x"), "acme/skills"));
    assertThrows(IllegalArgumentException.class,
        () -> files.saveScripts("triage", scripts("/abs.sh", "x"), "acme/skills"));
    assertThrows(IllegalArgumentException.class,
        () -> files.saveScripts("triage", scripts(SkillScripts.INSTALLED_MARKER, "forged"), "acme/skills"));

    assertFalse(Files.exists(owned.resolve("escaped.sh")));
    assertTrue(files.scriptsOf("triage").isEmpty());
  }

  @Test
  void scriptsTheOwnerPlacedByHandAreNotMarkedAsInstalled() throws IOException {
    Path dir = Files.createDirectories(owned.resolve("triage").resolve("scripts"));
    Files.writeString(dir.resolve("mine.sh"), "#!/bin/sh\n");

    SkillScripts found = files.scriptsOf("triage").orElseThrow();

    assertFalse(found.installed());
  }

  @Test
  void aSkillWithoutAScriptsFolderHasNoScripts() {
    assertEquals(Optional.empty(), files.scriptsOf("triage"));
    assertEquals(Optional.empty(), files.scriptsOf("unknown"));
  }

  @Test
  void theAgentRewritingTheSkillMdCannotWashTheInstalledMarkerAway() {
    files.saveScripts("triage", scripts("run.sh", "x"), "acme/skills");

    files.save(new Skill("triage", "Triage issues", "A different body."));

    assertTrue(files.scriptsOf("triage").orElseThrow().installed());
  }

  @Test
  void deletingTheSkillAlsoDeletesItsScripts() {
    files.saveScripts("triage", scripts("run.sh", "x"), "acme/skills");

    assertTrue(files.delete("triage"));

    assertFalse(Files.exists(owned.resolve("triage")));
  }

  @Test
  void aScriptsFolderThatIsASymlinkIsNotFollowed() throws IOException {
    Path outside = Files.createDirectories(shared.resolve("elsewhere"));
    Files.writeString(outside.resolve("x.sh"), "x");
    Files.createSymbolicLink(owned.resolve("triage").resolve("scripts"), outside);

    assertTrue(files.scriptsOf("triage").isEmpty());
  }

  @Test
  void aWriteThatFailsHalfwayStillLeavesTheScriptsMarkedAsInstalled() {
    Map<String, byte[]> scripts = new LinkedHashMap<>();
    scripts.put("run", "#!/bin/sh".getBytes(StandardCharsets.UTF_8));
    scripts.put("run/inner.sh", "x".getBytes(StandardCharsets.UTF_8));

    assertThrows(RuntimeException.class, () -> files.saveScripts("triage", scripts, "https://example.com/s.zip"));

    assertTrue(files.scriptsOf("triage").orElseThrow().installed());
  }
}
