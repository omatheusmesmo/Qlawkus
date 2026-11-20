package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        new TestConfig(baseUrl(), ApprovalMode.HITL, wellKnownHosts, publishDir.toString()), store,
        baseUrl(), baseUrl());
  }

  private void stubGitHubFile(String repo, String branch, String path, String markdown) {
    server.stubFor(get(urlPathEqualTo("/" + repo + "/" + branch + "/" + path))
        .willReturn(aResponse().withStatus(200).withBody(markdown)));
  }

  private void stubGitHubTree(String repo, String branch, String treeJson) {
    server.stubFor(get(urlPathEqualTo("/repos/" + repo + "/git/trees/" + branch))
        .withQueryParam("recursive", equalTo("1"))
        .willReturn(okJson(treeJson)));
  }

  private static String skillMarkdown(String name, String description) {
    return "---\nname: " + name + "\ndescription: " + description + "\n---\n# " + name + "\nSteps.";
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
  void search_keepsSkillIdInTheInstallReference() {
    server.stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
        {"query":"triage","searchType":"fuzzy","skills":[
          {"id":"mattpocock/skills/triage","source":"mattpocock/skills","skillId":"triage","name":"triage","installs":1095452}
        ]}""")));

    List<SkillRef> hits = hub(Optional.empty()).search("triage", 10);

    assertEquals(1, hits.size());
    assertEquals("triage", hits.get(0).name());
    assertEquals("mattpocock/skills/triage", hits.get(0).source());
  }

  @Test
  void search_omitsHitsWhoseSourceIsABareHost() {
    server.stubFor(get(urlPathEqualTo("/api/search")).willReturn(okJson("""
        {"skills":[
          {"id":"code.deepline.com/deepline-triage","source":"code.deepline.com","skillId":"deepline-triage","name":"deepline-triage"},
          {"id":"mattpocock/skills/triage","source":"mattpocock/skills","skillId":"triage","name":"triage"}
        ]}""")));

    List<SkillRef> hits = hub(Optional.empty()).search("triage", 10);

    assertEquals(1, hits.size());
    assertEquals("mattpocock/skills/triage", hits.get(0).source());
  }

  @Test
  void install_resolvesNamedSkillInSkillsFolder() {
    stubGitHubFile("acme/skills", "main", "skills/triage/SKILL.md", skillMarkdown("triage", "Triage"));

    Skill saved = hub(Optional.empty()).install("acme/skills/triage");

    assertEquals("triage", saved.name());
    assertTrue(store.saved.containsKey("triage"));
  }

  @Test
  void install_resolvesNamedSkillInFolderNamedAfterTheSkillOnMasterBranch() {
    stubGitHubFile("acme/skills", "master", "triage/SKILL.md", skillMarkdown("triage", "Triage"));

    Skill saved = hub(Optional.empty()).install("acme/skills/triage");

    assertEquals("triage", saved.name());
    assertTrue(store.saved.containsKey("triage"));
  }

  @Test
  void install_prefersTheNamedSkillOverARootSkillMd() {
    stubGitHubFile("acme/skills", "main", "SKILL.md", skillMarkdown("umbrella", "Index of skills"));
    stubGitHubFile("acme/skills", "main", "skills/triage/SKILL.md", skillMarkdown("triage", "Triage"));

    Skill saved = hub(Optional.empty()).install("acme/skills/triage");

    assertEquals("triage", saved.name());
    assertFalse(store.saved.containsKey("umbrella"));
  }

  @Test
  void install_findsNestedSkillThroughTheRepositoryTree() {
    stubGitHubTree("acme/skills", "main", """
        {"truncated":false,"tree":[
          {"path":"README.md","type":"blob"},
          {"path":"skills/engineering/triage","type":"tree"},
          {"path":"skills/engineering/triage/SKILL.md","type":"blob"},
          {"path":"skills/engineering/tdd/SKILL.md","type":"blob"}
        ]}""");
    stubGitHubFile("acme/skills", "main", "skills/engineering/triage/SKILL.md",
        skillMarkdown("triage", "Triage"));

    Skill saved = hub(Optional.empty()).install("acme/skills/triage");

    assertEquals("triage", saved.name());
    assertTrue(store.saved.containsKey("triage"));
  }

  @Test
  void install_prefersANestedNamedSkillOverARootSkillMdOfAnotherSkill() {
    stubGitHubFile("acme/skills", "main", "SKILL.md", skillMarkdown("umbrella", "Index of skills"));
    stubGitHubTree("acme/skills", "main", """
        {"truncated":false,"tree":[
          {"path":"skills/engineering/triage/SKILL.md","type":"blob"}
        ]}""");
    stubGitHubFile("acme/skills", "main", "skills/engineering/triage/SKILL.md",
        skillMarkdown("triage", "Triage"));

    Skill saved = hub(Optional.empty()).install("acme/skills/triage");

    assertEquals("triage", saved.name());
    assertFalse(store.saved.containsKey("umbrella"));
  }

  @Test
  void install_refusesARootSkillMdThatIsAnotherSkill() {
    stubGitHubFile("acme/skills", "main", "SKILL.md", skillMarkdown("umbrella", "Index of skills"));
    stubGitHubTree("acme/skills", "main", "{\"truncated\":false,\"tree\":[]}");

    assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install("acme/skills/triage"));
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void install_acceptsARootSkillMdThatIsTheNamedSkill() {
    stubGitHubFile("acme/solo", "main", "SKILL.md", skillMarkdown("solo", "The only skill"));
    stubGitHubTree("acme/solo", "main", "{\"truncated\":false,\"tree\":[]}");

    Skill saved = hub(Optional.empty()).install("acme/solo/solo");

    assertEquals("solo", saved.name());
  }

  @Test
  void install_refusesWhenTheTreeHoldsSeveralMatchingSkills() {
    stubGitHubTree("acme/skills", "main", """
        {"truncated":false,"tree":[
          {"path":"skills/engineering/triage/SKILL.md","type":"blob"},
          {"path":"skills/personal/triage/SKILL.md","type":"blob"}
        ]}""");

    assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install("acme/skills/triage"));
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void install_failureNamesTheSourceAndThePathsTried() {
    stubGitHubTree("acme/skills", "main", "{\"truncated\":false,\"tree\":[]}");
    stubGitHubTree("acme/skills", "master", "{\"truncated\":false,\"tree\":[]}");

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install("acme/skills/triage"));

    assertTrue(failure.getMessage().contains("acme/skills/triage"), failure.getMessage());
    assertTrue(failure.getMessage().contains("skills/triage/SKILL.md"), failure.getMessage());
    assertTrue(failure.getMessage().contains("triage/SKILL.md"), failure.getMessage());
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void install_ownerRepoStillResolvesTheRootSkillMd() {
    stubGitHubFile("acme/skills", "main", "SKILL.md", skillMarkdown("umbrella", "Root skill"));

    Skill saved = hub(Optional.empty()).install("acme/skills");

    assertEquals("umbrella", saved.name());
  }

  @Test
  void install_rejectsABareHostSource() {
    assertThrows(IllegalArgumentException.class,
        () -> hub(Optional.empty()).install("code.deepline.com"));
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
