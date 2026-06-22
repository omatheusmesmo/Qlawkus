package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link HttpSkillHub} against a standalone WireMock server: registry search,
 * well-known discovery, install frontmatter parsing, path-traversal rejection, and publish. No
 * Quarkus boot.
 */
class HttpSkillHubTest {

  @TempDir
  Path publishDir;

  private WireMockServer server;
  private RecordingSkillStore store;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(options().dynamicPort());
    server.start();
    store = new RecordingSkillStore();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private String baseUrl() {
    return "http://localhost:" + server.port();
  }

  private HttpSkillHub hub(Optional<List<String>> wellKnownHosts) {
    return new HttpSkillHub(
        new TestConfig(baseUrl(), ApprovalMode.HITL, wellKnownHosts, publishDir.toString()), store);
  }

  @Test
  void search_parsesTopLevelArray() {
    server.stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
        [
          {"name":"triage-inbox","description":"Triage your inbox","source":"acme/triage-inbox"},
          {"name":"post-update","description":"Post a weekly update","source":"acme/post-update"}
        ]""")));

    List<SkillRef> hits = hub(Optional.empty()).search("triage", 10);

    assertEquals(2, hits.size());
    assertEquals("triage-inbox", hits.get(0).name());
    assertEquals("acme/triage-inbox", hits.get(0).source());
    assertEquals("Triage your inbox", hits.get(0).description());
  }

  @Test
  void search_honorsLimit() {
    server.stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
        {"results":[
          {"name":"a","description":"","source":"x/a"},
          {"name":"b","description":"","source":"x/b"},
          {"name":"c","description":"","source":"x/c"}
        ]}""")));

    assertEquals(2, hub(Optional.empty()).search("any", 2).size());
  }

  @Test
  void search_discoversFromWellKnownIndex() {
    server.stubFor(get(urlPathEqualTo("/api/search"))
        .willReturn(aResponse().withStatus(404)));
    server.stubFor(get(urlPathEqualTo("/.well-known/agent-skills/index.json")).willReturn(okJson("""
        {"skills":[
          {"url":"skills/triage-inbox/SKILL.md","description":"Triage your inbox"},
          {"url":"https://other.example/cleanup/SKILL.md","name":"cleanup"}
        ]}""")));

    List<SkillRef> hits = hub(Optional.of(List.of(baseUrl()))).search("triage", 10);

    assertEquals(1, hits.size());
    assertEquals("triage-inbox", hits.get(0).name());
    assertTrue(hits.get(0).source().endsWith("/skills/triage-inbox/SKILL.md"));
  }

  @Test
  void install_parsesFrontmatterAndSaves() {
    server.stubFor(get(urlPathEqualTo("/skills/foo/SKILL.md")).willReturn(aResponse()
        .withStatus(200)
        .withBody("""
            ---
            name: foo-skill
            description: Does foo
            ---
            # Foo
            Do the foo.""")));

    Skill saved = hub(Optional.empty()).install(baseUrl() + "/skills/foo/SKILL.md");

    assertEquals("foo-skill", saved.name());
    assertEquals("Does foo", saved.description());
    assertTrue(saved.body().contains("# Foo"));
    assertTrue(store.saved.containsKey("foo-skill"));
  }

  @Test
  void install_rejectsPathTraversalName() {
    server.stubFor(get(urlPathEqualTo("/skills/evil/SKILL.md")).willReturn(aResponse()
        .withStatus(200)
        .withBody("""
            ---
            name: ../../etc/evil
            description: nope
            ---
            body""")));

    assertThrows(IllegalArgumentException.class,
        () -> hub(Optional.empty()).install(baseUrl() + "/skills/evil/SKILL.md"));
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void install_throwsOnNotFound() {
    assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install(baseUrl() + "/skills/missing/SKILL.md"));
  }

  @Test
  void publish_writesRenderedSkillMd() throws Exception {
    Path written = hub(Optional.empty())
        .publish(new Skill("my-skill", "Does my thing", "# My Skill\nSteps."));

    assertEquals(publishDir.resolve("my-skill").resolve("SKILL.md"), written);
    String content = Files.readString(written);
    assertTrue(content.contains("name: my-skill"));
    assertTrue(content.contains("# My Skill"));
  }

  @Test
  void publish_rejectsPathTraversalName() {
    assertThrows(IllegalArgumentException.class,
        () -> hub(Optional.empty()).publish(new Skill("../evil", "x", "y")));
  }

  private record TestConfig(String baseUrl, ApprovalMode approvalMode,
      Optional<List<String>> wellKnownHosts, String publishDir) implements SkillHubConfig {
    @Override
    public boolean enabled() {
      return true;
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
    public Publish publish() {
      return new TestPublish(publishDir);
    }
  }

  private record TestPublish(String dir) implements SkillHubConfig.Publish {
    @Override
    public boolean enabled() {
      return true;
    }
  }
}
