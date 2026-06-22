package dev.omatheusmesmo.qlawkus.it.skillhub;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.skillhub.HttpSkillHub;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHub;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubTool;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillRef;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end wiring of the optional {@code qlawkus-tools-skill-hub} extension inside a booted agent
 * (markdown stores, no datasource): the {@code @DefaultBean} {@link SkillHub} resolves, the
 * {@link SkillHubConfig} mapping is honored, the {@link SkillHubTool} is a live {@code @QlawTool}
 * bean, and search/install/publish work against WireMock through the real {@link SkillStore}.
 */
@QuarkusTest
@ConnectWireMock
class SkillHubTest {

  WireMock wiremock;

  @Inject
  SkillHub skillHub;

  @Inject
  @QlawTool
  SkillHubTool skillHubTool;

  @Inject
  SkillStore skillStore;

  @Inject
  SkillHubConfig config;

  @BeforeEach
  void setupStubs() {
    wiremock.register(WireMock.get(WireMock.urlPathEqualTo("/api/search"))
        .willReturn(WireMock.okJson("""
            [{"name":"triage","description":"Triage your inbox","source":"acme/triage"}]""")));

    wiremock.register(WireMock.get(WireMock.urlEqualTo("/.well-known/agent-skills/index.json"))
        .willReturn(WireMock.okJson("""
            {"skills":[{"url":"skills/wk/SKILL.md","name":"wk-skill","description":"From well-known"}]}""")));

    wiremock.register(WireMock.get(WireMock.urlEqualTo("/skills/foo/SKILL.md"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody("""
            ---
            name: foo-skill
            description: Does foo
            ---
            # Foo
            Do the foo.""")));
  }

  @Test
  void defaultBean_andToolBean_areWired() {
    assertInstanceOf(HttpSkillHub.class, skillHub,
        "the @DefaultBean SkillHub should resolve to HttpSkillHub");
    assertNotNull(skillHubTool, "SkillHubTool must be a live @QlawTool bean at runtime");
  }

  @Test
  void search_mergesRegistryAndWellKnown() {
    List<SkillRef> hits = skillHub.search("", 10);

    assertTrue(hits.stream().anyMatch(h -> h.name().equals("triage")), "registry hit expected");
    assertTrue(hits.stream().anyMatch(h -> h.name().equals("wk-skill")), "well-known hit expected");
  }

  @Test
  void install_savesIntoMarkdownStore() {
    Skill saved = skillHub.install(config.baseUrl() + "/skills/foo/SKILL.md");

    assertTrue(saved.name().equals("foo-skill"));
    assertTrue(skillStore.get("foo-skill").isPresent(), "installed skill should be in the store");
    assertTrue(Files.isRegularFile(Path.of("target/skill-hub/skills/foo-skill/SKILL.md")),
        "installed skill should be persisted as a SKILL.md file");
  }

  @Test
  void installSkillTool_yolo_installsWithoutConfirmation() {
    String result = skillHubTool.installSkill(config.baseUrl() + "/skills/foo/SKILL.md", false);

    assertTrue(result.contains("Installed skill: foo-skill"), result);
    assertTrue(skillStore.get("foo-skill").isPresent());
  }

  @Test
  void publishSkillTool_writesToConfiguredDir() {
    skillStore.save(new Skill("pub-skill", "Publishes a thing", "# Pub\nSteps."));

    String result = skillHubTool.publishSkill("pub-skill", false);

    assertTrue(result.contains("Published skill 'pub-skill'"), result);
    assertTrue(Files.isRegularFile(Path.of("target/skill-hub/published/pub-skill/SKILL.md")),
        "published skill should be written under the configured publish dir");
  }
}
