package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveSkillReader.ArchivedSkill;
import dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveSkillReader.Limits;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.file;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.markAsSymlink;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.tarEntry;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.tarFile;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.tarGz;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.zip;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the archive reader's guarantees: only SKILL.md text is read, and every structural hazard
 * (traversal, absolute or backslash names, symlinks, too many entries, oversized expansion) is refused
 * before any skill content is returned. Archives are built in memory, the way the reader receives them.
 */
class ArchiveSkillReaderTest {

  private static final Limits LIMITS = new Limits(100, 1024 * 1024, 64 * 1024);

  private static final String TRIAGE = "---\nname: triage\ndescription: Triage\n---\nSteps.";
  private static final String TEXT = "---\nname: text\ndescription: Text\n---\nMore.";

  @Test
  void aZipWithSeveralSkillFoldersYieldsEachSkillMd() throws IOException {
    byte[] zip = zip(file("skills/triage/SKILL.md", TRIAGE), file("skills/text/SKILL.md", TEXT),
        file("skills/triage/scripts/run.sh", "rm -rf /"));

    List<ArchivedSkill> skills = ArchiveSkillReader.read(zip, LIMITS);

    assertEquals(2, skills.size());
    assertEquals("skills/triage", skills.get(0).folder());
    assertEquals(TRIAGE, skills.get(0).content());
    assertEquals("skills/text", skills.get(1).folder());
  }

  @Test
  void aRootSkillMdHasAnEmptyFolder() throws IOException {
    List<ArchivedSkill> skills = ArchiveSkillReader.read(zip(file("SKILL.md", TRIAGE)), LIMITS);

    assertEquals(1, skills.size());
    assertEquals("", skills.get(0).folder());
  }

  @Test
  void aTarGzYieldsItsSkillMdAndIgnoresOtherEntries() throws IOException {
    byte[] tgz = tarGz(tarFile("bundle/triage/SKILL.md", TRIAGE), tarFile("bundle/triage/assets/x.bin", "bytes"));

    List<ArchivedSkill> skills = ArchiveSkillReader.read(tgz, LIMITS);

    assertEquals(1, skills.size());
    assertEquals("bundle/triage", skills.get(0).folder());
    assertEquals(TRIAGE, skills.get(0).content());
  }

  @Test
  void anUnrecognizedFormatIsRefused() {
    byte[] text = "just some text, not an archive".getBytes(StandardCharsets.UTF_8);

    assertThrows(IllegalArgumentException.class, () -> ArchiveSkillReader.read(text, LIMITS));
  }

  @Test
  void aZipEntryThatEscapesTheArchiveIsRefused() throws IOException {
    byte[] zip = zip(file("../evil/SKILL.md", TRIAGE));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip, LIMITS));
    assertTrue(refused.getMessage().contains("../evil/SKILL.md"), refused.getMessage());
  }

  @Test
  void anAbsoluteOrBackslashZipEntryIsRefused() throws IOException {
    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip(file("/etc/SKILL.md", TRIAGE)), LIMITS));
    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip(file("skills\\..\\evil\\SKILL.md", TRIAGE)), LIMITS));
    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip(file("C:/evil/SKILL.md", TRIAGE)), LIMITS));
  }

  @Test
  void aZipSymlinkIsRefusedEvenWhenItsNameIsSkillMd() throws IOException {
    byte[] zip = zip(file("skills/link/SKILL.md", "../../secrets/SKILL.md"));
    markAsSymlink(zip);

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip, LIMITS));
    assertTrue(refused.getMessage().toLowerCase().contains("symlink"), refused.getMessage());
  }

  @Test
  void aTarSymlinkIsRefused() throws IOException {
    byte[] tgz = tarGz(tarEntry("bundle/link/SKILL.md", '2', "../../secrets/SKILL.md"));

    assertThrows(IllegalArgumentException.class, () -> ArchiveSkillReader.read(tgz, LIMITS));
  }

  @Test
  void aTarEntryThatEscapesTheArchiveIsRefused() throws IOException {
    byte[] tgz = tarGz(tarFile("../../etc/SKILL.md", TRIAGE));

    assertThrows(IllegalArgumentException.class, () -> ArchiveSkillReader.read(tgz, LIMITS));
  }

  @Test
  void anArchiveWithTooManyEntriesIsRefused() throws IOException {
    byte[] zip = zip(file("a/SKILL.md", TRIAGE), file("b/SKILL.md", TEXT), file("c/notes.md", "x"));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip, new Limits(2, 1024 * 1024, 64 * 1024)));
    assertTrue(refused.getMessage().contains("limit"), refused.getMessage());
  }

  @Test
  void aZipWhoseDeclaredExpansionIsTooLargeIsRefused() throws IOException {
    byte[] zip = zip(file("a/SKILL.md", "x".repeat(4096)), file("a/big.txt", "y".repeat(4096)));

    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip, new Limits(100, 4096, 64 * 1024)));
  }

  @Test
  void aTarGzWhoseExpansionIsTooLargeIsRefused() throws IOException {
    byte[] tgz = tarGz(tarFile("a/big.txt", "y".repeat(8192)), tarFile("a/SKILL.md", TRIAGE));

    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(tgz, new Limits(100, 4096, 64 * 1024)));
  }

  @Test
  void aSkillMdOverTheSizeLimitIsRefused() throws IOException {
    byte[] zip = zip(file("big/SKILL.md", "x".repeat(2048)));

    assertThrows(IllegalArgumentException.class,
        () -> ArchiveSkillReader.read(zip, new Limits(100, 1024 * 1024, 1024)));
  }
}
