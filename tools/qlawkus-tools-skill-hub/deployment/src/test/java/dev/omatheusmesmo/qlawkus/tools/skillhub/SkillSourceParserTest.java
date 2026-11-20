package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.ArchiveSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.FileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GistSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFolderSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubSkillSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pins how every pasted or registry source string maps to the structured {@link SkillSource}. */
class SkillSourceParserTest {

  @Test
  void ownerRepoIsTheRepositoryRoot() {
    assertEquals(new GitHubFolderSource("acme/skills", null, ""), SkillSourceParser.parse("acme/skills"));
  }

  @Test
  void ownerRepoSkillIsANamedSkill() {
    assertEquals(new GitHubSkillSource("acme/skills", "triage"),
        SkillSourceParser.parse("acme/skills/triage"));
  }

  @Test
  void ownerRepoWithSeveralSegmentsIsAFolder() {
    assertEquals(new GitHubFolderSource("acme/skills", null, "skills/triage"),
        SkillSourceParser.parse("acme/skills/skills/triage"));
  }

  @Test
  void githubRepositoryPageIsTheRepositoryRoot() {
    assertEquals(new GitHubFolderSource("acme/skills", null, ""),
        SkillSourceParser.parse("https://github.com/acme/skills"));
  }

  @Test
  void githubBlobUrlIsAFileOnARef() {
    assertEquals(new GitHubFileSource("acme/skills", "main", "skills/triage/SKILL.md"),
        SkillSourceParser.parse("https://github.com/acme/skills/blob/main/skills/triage/SKILL.md"));
  }

  @Test
  void githubTreeUrlIsAFolderOnARef() {
    assertEquals(new GitHubFolderSource("acme/skills", "main", "skills/triage"),
        SkillSourceParser.parse("https://github.com/acme/skills/tree/main/skills/triage"));
  }

  @Test
  void githubRawUrlIsAPlainFileUrl() {
    String raw = "https://raw.githubusercontent.com/acme/skills/main/skills/triage/SKILL.md";
    assertEquals(new FileSource(raw), SkillSourceParser.parse(raw));
  }

  @Test
  void skillsShSkillPageIsANamedSkill() {
    assertEquals(new GitHubSkillSource("acme/skills", "triage"),
        SkillSourceParser.parse("https://skills.sh/acme/skills/triage"));
  }

  @Test
  void skillsShRepositoryPageIsTheRepositoryRoot() {
    assertEquals(new GitHubFolderSource("acme/skills", null, ""),
        SkillSourceParser.parse("https://skills.sh/acme/skills"));
  }

  @Test
  void gitlabBlobUrlBecomesItsRawFile() {
    assertEquals(new FileSource("https://gitlab.com/group/proj/-/raw/main/skills/x/SKILL.md"),
        SkillSourceParser.parse("https://gitlab.com/group/proj/-/blob/main/skills/x/SKILL.md"));
  }

  @Test
  void gitlabTreeUrlBecomesTheFolderSkillFile() {
    assertEquals(new FileSource("https://gitlab.com/group/proj/-/raw/main/skills/x/SKILL.md"),
        SkillSourceParser.parse("https://gitlab.com/group/proj/-/tree/main/skills/x"));
  }

  @Test
  void bitbucketSrcUrlBecomesItsRawFile() {
    assertEquals(new FileSource("https://bitbucket.org/ws/repo/raw/main/skills/x/SKILL.md"),
        SkillSourceParser.parse("https://bitbucket.org/ws/repo/src/main/skills/x/SKILL.md"));
  }

  @Test
  void gistUrlIsAGist() {
    assertEquals(new GistSource("0123abcd"),
        SkillSourceParser.parse("https://gist.github.com/someone/0123abcd"));
    assertEquals(new GistSource("0123abcd"),
        SkillSourceParser.parse("https://gist.github.com/0123abcd"));
  }

  @Test
  void anUnknownHttpsSkillUrlStillWorks() {
    assertEquals(new FileSource("https://example.com/skills/triage/SKILL.md"),
        SkillSourceParser.parse("https://example.com/skills/triage/SKILL.md"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://example.com/bundle.zip",
      "https://example.com/bundle.zip?download=1",
      "https://example.com/bundle.tar.gz",
      "https://example.com/bundle.TGZ"})
  void archiveUrlsAreArchives(String url) {
    assertEquals(new ArchiveSource(url), SkillSourceParser.parse(url));
  }

  @Test
  void aPlainNameIsANameToSearchFor() {
    assertEquals(new NameSource("triage"), SkillSourceParser.parse("triage"));
  }

  @Test
  void surroundingWhitespaceIsIgnored() {
    assertEquals(new NameSource("triage"), SkillSourceParser.parse("  triage  "));
  }

  @Test
  void anHttpLinkIsParsedAndTheFetchPolicyRefusesIt() {
    assertEquals(new FileSource("http://example.com/SKILL.md"),
        SkillSourceParser.parse("http://example.com/SKILL.md"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "ftp://example.com/SKILL.md",
      "https://user:secret@example.com/SKILL.md",
      "code.deepline.com",
      "example.com/SKILL.md",
      "https://github.com/acme",
      "acme/skills/../../etc",
      "https://github.com/acme/skills/blob/main",
      "",
      "   "})
  void unusableSourcesAreRefusedWithAReason(String source) {
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> SkillSourceParser.parse(source));
    assertFalse(refused.getMessage().isBlank());
  }
}
