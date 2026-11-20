package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SkillHubTool}: the YOLO/HITL approval gating for every install path, name
 * resolution, the per-call cap, partial failures, provenance-based update, publish, and the switches.
 */
class SkillHubToolTest {

  @Test
  void hitl_previewsWithoutInstalling_untilConfirmed() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL, 10));

    String preview = tool.installSkill("acme/foo", false);
    assertFalse(hub.installed.contains("acme/foo"), "HITL must not install before confirmation");
    assertTrue(preview.toLowerCase().contains("approve"));
    assertTrue(preview.contains("confirm=true"));

    String done = tool.installSkill("acme/foo", true);
    assertTrue(hub.installed.contains("acme/foo"), "HITL installs once confirmed");
    assertEquals("Installed skill: foo", done);
  }

  @Test
  void yolo_installsImmediately() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    String done = tool.installSkill("acme/foo", false);

    assertTrue(hub.installed.contains("acme/foo"), "YOLO installs without confirmation");
    assertEquals("Installed skill: foo", done);
  }

  @Test
  void failedInstall_propagatesSoTheFrameworkReportsAnError() {
    RecordingHub hub = new RecordingHub();
    hub.failure = new IllegalStateException("Could not find a SKILL.md for acme/skills/triage");
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> tool.installSkill("acme/skills/triage", true));

    assertEquals(hub.failure, thrown);
  }

  @Test
  void aPlainNameWithOneHitPreviewsThatHitThenInstallsIt() {
    RecordingHub hub = new RecordingHub();
    hub.searchHits = List.of(new SkillRef("triage", "Triage", "acme/skills/triage"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL, 10));

    String preview = tool.installSkill("triage", false);
    assertTrue(preview.contains("acme/skills/triage"), preview);
    assertTrue(hub.installed.isEmpty(), "a name still needs the owner's approval");

    String done = tool.installSkill("triage", true);
    assertEquals(List.of("acme/skills/triage"), hub.installed);
    assertEquals("Installed skill: triage", done);
  }

  @Test
  void aPlainNameWithSeveralHitsAsksWhichInsteadOfFailing() {
    RecordingHub hub = new RecordingHub();
    hub.searchHits = List.of(
        new SkillRef("triage-inbox", "Triage inbox", "acme/a/triage-inbox"),
        new SkillRef("triage-review", "Review triage", "acme/b/triage-review"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    String answer = tool.installSkill("triage", true);

    assertTrue(answer.contains("acme/a/triage-inbox") && answer.contains("acme/b/triage-review"), answer);
    assertTrue(answer.toLowerCase().contains("which"), answer);
    assertTrue(hub.installed.isEmpty());
  }

  @Test
  void aPlainNameWithOneExactNameAmongSeveralHitsIsClear() {
    RecordingHub hub = new RecordingHub();
    hub.searchHits = List.of(
        new SkillRef("triage-review", "Review triage", "acme/b/triage-review"),
        new SkillRef("triage", "Triage inbox", "acme/a/triage"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    tool.installSkill("triage", true);

    assertEquals(List.of("acme/a/triage"), hub.installed);
  }

  @Test
  void aPlainNameWithNoHitFailsWithTheReason() {
    RecordingHub hub = new RecordingHub();
    hub.searchHits = List.of();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> tool.installSkill("nothing-like-this", true));

    assertTrue(failure.getMessage().contains("nothing-like-this"), failure.getMessage());
  }

  @Test
  void aBadSourceIsRefusedWithoutAPreview() {
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL, 10));

    assertThrows(IllegalArgumentException.class, () -> tool.installSkill("ftp://example.com/SKILL.md", false));
  }

  @Test
  void aFolderSourceInstallsTheNamedSkillsUnderOneApproval() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"), candidate("tdd"), candidate("review"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL, 10));

    String preview = tool.installSkillsFromSource("acme/skills", "triage, tdd", false);
    assertTrue(preview.contains("triage") && preview.contains("tdd"), preview);
    assertTrue(hub.candidatesInstalled.isEmpty(), "the preview must not install");

    String done = tool.installSkillsFromSource("acme/skills", "triage, tdd", true);

    assertEquals(List.of("triage", "tdd"), hub.candidatesInstalled);
    assertTrue(done.contains("triage") && done.contains("tdd"), done);
  }

  @Test
  void allInstallsEverySkillInTheSource() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"), candidate("tdd"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    tool.installSkillsFromSource("acme/skills", "all", false);

    assertEquals(List.of("triage", "tdd"), hub.candidatesInstalled);
  }

  @Test
  void aSelectionOverTheCapIsRefusedBeforeAnythingIsInstalled() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("a"), candidate("b"), candidate("c"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 2));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> tool.installSkillsFromSource("acme/skills", "all", true));

    assertTrue(refused.getMessage().contains("2"), refused.getMessage());
    assertTrue(hub.candidatesInstalled.isEmpty());
  }

  @Test
  void aPartialFailureSaysWhatWasInstalledAndWhatWasNot() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"), candidate("broken"), candidate("tdd"));
    hub.failingCandidates = Set.of("broken");
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> tool.installSkillsFromSource("acme/skills", "all", true));

    assertTrue(failure.getMessage().contains("triage") && failure.getMessage().contains("tdd"),
        failure.getMessage());
    assertTrue(failure.getMessage().contains("broken"), failure.getMessage());
    assertEquals(List.of("triage", "tdd"), hub.candidatesInstalled);
  }

  @Test
  void anUnknownNameInTheSelectionListsWhatTheSourceHolds() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> tool.installSkillsFromSource("acme/skills", "nope", true));

    assertTrue(refused.getMessage().contains("triage"), refused.getMessage());
    assertTrue(hub.candidatesInstalled.isEmpty());
  }

  @Test
  void listSkillsInSourceNamesEachSkillTheSourceHolds() {
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"), candidate("tdd"));
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL, 10));

    String listing = tool.listSkillsInSource("acme/skills");

    assertTrue(listing.contains("triage") && listing.contains("tdd"), listing);
    assertTrue(listing.contains("2 skill"), listing);
  }

  @Test
  void pastedSkillMarkdownIsPreviewedThenSaved() {
    RecordingSkillStore store = new RecordingSkillStore();
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), store,
        new TestConfig(true, false, ApprovalMode.HITL, 10));
    String text = "---\nname: pasted\ndescription: From chat\n---\n# Pasted\nSteps.";

    String preview = tool.installSkillFromText(text, false);
    assertFalse(store.saved.containsKey("pasted"), "HITL must not save before confirmation");
    assertTrue(preview.contains("pasted") && preview.contains("confirm=true"), preview);

    String done = tool.installSkillFromText(text, true);
    assertTrue(store.saved.containsKey("pasted"));
    assertEquals("Installed skill: pasted", done);
  }

  @Test
  void pastedSkillMarkdownWithoutANameIsRefused() {
    RecordingSkillStore store = new RecordingSkillStore();
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), store,
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> tool.installSkillFromText("# No frontmatter at all", true));

    assertTrue(refused.getMessage().contains("name"), refused.getMessage());
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void pastedSkillMarkdownOverTheSizeCapIsRefused() {
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10, 64));

    assertThrows(IllegalArgumentException.class,
        () -> tool.installSkillFromText("---\nname: big\n---\n" + "x".repeat(200), true));
  }

  @Test
  void updateReinstallsFromTheRecordedSource() {
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("foo", "does foo", "# Foo"));
    store.recordOrigin("foo", new SkillOrigin("acme/foo", null, 1L));
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, store, new TestConfig(true, false, ApprovalMode.HITL, 10));

    String preview = tool.updateSkill("foo", false);
    assertTrue(hub.installed.isEmpty(), "HITL must not update before confirmation");
    assertTrue(preview.contains("acme/foo"), preview);

    tool.updateSkill("foo", true);
    assertEquals(List.of("acme/foo"), hub.installed);
  }

  @Test
  void updateOfAFolderSkillReselectsItByItsCandidateName() {
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("tdd", "Test first", "# TDD"));
    store.recordOrigin("tdd", new SkillOrigin("acme/skills", "tdd", 1L));
    RecordingHub hub = new RecordingHub();
    hub.discovered = List.of(candidate("triage"), candidate("tdd"));
    SkillHubTool tool = new SkillHubTool(hub, store, new TestConfig(true, false, ApprovalMode.YOLO, 10));

    tool.updateSkill("tdd", true);

    assertEquals(List.of("tdd"), hub.candidatesInstalled);
  }

  @Test
  void updateWithoutARecordedSourceExplainsWhy() {
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("handwritten", "x", "# x"));
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), store,
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> tool.updateSkill("handwritten", true));

    assertTrue(failure.getMessage().contains("No recorded source"), failure.getMessage());
  }

  @Test
  void listSkillSourcesShowsWhereEachSkillCameFrom() {
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("foo", "does foo", "# Foo"));
    store.recordOrigin("foo", new SkillOrigin("acme/foo", null, 1_700_000_000_000L));
    store.save(new Skill("bar", "does bar", "# Bar"));
    SkillHubTool tool = new SkillHubTool(new RecordingHub(), store,
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    String listing = tool.listSkillSources();

    assertTrue(listing.contains("foo") && listing.contains("acme/foo"), listing);
    assertTrue(listing.contains("bar") && listing.contains("no recorded source"), listing);
  }

  @Test
  void disabled_refusesAllTools() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(false, true, ApprovalMode.YOLO, 10));

    assertTrue(tool.searchSkillHub("anything").contains("disabled"));
    assertTrue(tool.installSkill("acme/foo", true).contains("disabled"));
    assertTrue(tool.installSkillsFromSource("acme/skills", "all", true).contains("disabled"));
    assertTrue(tool.installSkillFromText("---\nname: x\n---\n", true).contains("disabled"));
    assertTrue(tool.listSkillsInSource("acme/skills").contains("disabled"));
    assertTrue(tool.publishSkill("foo", true).contains("disabled"));
    assertFalse(hub.installed.contains("acme/foo"));
    assertFalse(hub.searched);
    assertFalse(hub.published);
  }

  @Test
  void publish_refusesWhenPublishDisabled() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO, 10));

    assertTrue(tool.publishSkill("foo", true).contains("Publishing is disabled"));
    assertFalse(hub.published);
  }

  @Test
  void publish_hitl_previewsThenWrites() {
    RecordingHub hub = new RecordingHub();
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("foo", "does foo", "# Foo"));
    SkillHubTool tool = new SkillHubTool(hub, store, new TestConfig(true, true, ApprovalMode.HITL, 10));

    String preview = tool.publishSkill("foo", false);
    assertFalse(hub.published, "HITL must not publish before confirmation");
    assertTrue(preview.contains("confirm=true"));

    String done = tool.publishSkill("foo", true);
    assertTrue(hub.published, "HITL publishes once confirmed");
    assertTrue(done.contains("Published skill 'foo'"));
  }

  @Test
  void publish_unknownSkill_returnsNotFound() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, true, ApprovalMode.YOLO, 10));

    assertTrue(tool.publishSkill("nope", true).contains("No skill named"));
    assertFalse(hub.published);
  }

  private static SkillCandidate candidate(String name) {
    return new SkillCandidate(name, "acme/skills", "https://example.test/" + name + "/SKILL.md", null);
  }

  private static final class RecordingHub implements SkillHub {
    final List<String> installed = new ArrayList<>();
    final List<String> candidatesInstalled = new ArrayList<>();
    List<SkillRef> searchHits = List.of(new SkillRef("foo", "does foo", "acme/foo"));
    List<SkillCandidate> discovered = List.of();
    Set<String> failingCandidates = Set.of();
    boolean searched;
    boolean published;
    RuntimeException failure;

    @Override
    public List<SkillRef> search(String query, int limit) {
      searched = true;
      return searchHits;
    }

    @Override
    public Skill install(String source) {
      installed.add(source);
      if (failure != null) {
        throw failure;
      }
      String name = source.substring(source.lastIndexOf('/') + 1);
      return new Skill(name, "does " + name, "# " + name);
    }

    @Override
    public List<SkillCandidate> discover(String source) {
      return discovered;
    }

    @Override
    public Skill installCandidate(SkillCandidate candidate) {
      if (failingCandidates.contains(candidate.name())) {
        throw new IllegalStateException("could not fetch " + candidate.name());
      }
      candidatesInstalled.add(candidate.name());
      return new Skill(candidate.name(), "x", "# " + candidate.name());
    }

    @Override
    public Path publish(Skill skill) {
      published = true;
      return Path.of("/tmp/published", skill.name(), "SKILL.md");
    }
  }

  private record TestConfig(boolean enabled, boolean publishEnabled, ApprovalMode approvalMode,
      int maxInstallsPerCall, long maxSkillBytes) implements SkillHubConfig {

    TestConfig(boolean enabled, boolean publishEnabled, ApprovalMode approvalMode, int maxInstallsPerCall) {
      this(enabled, publishEnabled, approvalMode, maxInstallsPerCall, 262_144L);
    }

    @Override
    public String baseUrl() {
      return "http://localhost";
    }

    @Override
    public int maxResults() {
      return 10;
    }

    @Override
    public Duration requestTimeout() {
      return Duration.ofSeconds(5);
    }

    @Override
    public Optional<List<String>> wellKnownHosts() {
      return Optional.empty();
    }

    @Override
    public Publish publish() {
      return new TestPublish(publishEnabled);
    }

    @Override
    public Archive archive() {
      return new Archive() {
        @Override
        public long maxBytes() {
          return 10_485_760L;
        }

        @Override
        public int maxEntries() {
          return 500;
        }

        @Override
        public long maxExpandedBytes() {
          return 20_971_520L;
        }
      };
    }
  }

  private record TestPublish(boolean enabled) implements SkillHubConfig.Publish {
    @Override
    public String dir() {
      return "/tmp/published";
    }
  }
}
