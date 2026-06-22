package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SkillHubTool}: the YOLO/HITL approval gating, publish, and the switches. */
class SkillHubToolTest {

  @Test
  void hitl_previewsWithoutInstalling_untilConfirmed() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.HITL));

    String preview = tool.installSkill("acme/foo", false);
    assertFalse(hub.installed, "HITL must not install before confirmation");
    assertTrue(preview.toLowerCase().contains("approve"));
    assertTrue(preview.contains("confirm=true"));

    String done = tool.installSkill("acme/foo", true);
    assertTrue(hub.installed, "HITL installs once confirmed");
    assertEquals("Installed skill: foo", done);
  }

  @Test
  void yolo_installsImmediately() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO));

    String done = tool.installSkill("acme/foo", false);

    assertTrue(hub.installed, "YOLO installs without confirmation");
    assertEquals("Installed skill: foo", done);
  }

  @Test
  void disabled_refusesAllTools() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(false, true, ApprovalMode.YOLO));

    assertTrue(tool.searchSkillHub("anything").contains("disabled"));
    assertTrue(tool.installSkill("acme/foo", true).contains("disabled"));
    assertTrue(tool.publishSkill("foo", true).contains("disabled"));
    assertFalse(hub.installed);
    assertFalse(hub.searched);
    assertFalse(hub.published);
  }

  @Test
  void publish_refusesWhenPublishDisabled() {
    RecordingHub hub = new RecordingHub();
    SkillHubTool tool = new SkillHubTool(hub, new RecordingSkillStore(),
        new TestConfig(true, false, ApprovalMode.YOLO));

    assertTrue(tool.publishSkill("foo", true).contains("Publishing is disabled"));
    assertFalse(hub.published);
  }

  @Test
  void publish_hitl_previewsThenWrites() {
    RecordingHub hub = new RecordingHub();
    RecordingSkillStore store = new RecordingSkillStore();
    store.save(new Skill("foo", "does foo", "# Foo"));
    SkillHubTool tool = new SkillHubTool(hub, store, new TestConfig(true, true, ApprovalMode.HITL));

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
        new TestConfig(true, true, ApprovalMode.YOLO));

    assertTrue(tool.publishSkill("nope", true).contains("No skill named"));
    assertFalse(hub.published);
  }

  private static final class RecordingHub implements SkillHub {
    boolean installed;
    boolean searched;
    boolean published;

    @Override
    public List<SkillRef> search(String query, int limit) {
      searched = true;
      return List.of(new SkillRef("foo", "does foo", "acme/foo"));
    }

    @Override
    public Skill install(String source) {
      installed = true;
      return new Skill("foo", "does foo", "# Foo");
    }

    @Override
    public Path publish(Skill skill) {
      published = true;
      return Path.of("/tmp/published", skill.name(), "SKILL.md");
    }
  }

  private record TestConfig(boolean enabled, boolean publishEnabled, ApprovalMode approvalMode)
      implements SkillHubConfig {
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
  }

  private record TestPublish(boolean enabled) implements SkillHubConfig.Publish {
    @Override
    public String dir() {
      return "/tmp/published";
    }
  }
}
