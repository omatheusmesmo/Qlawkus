package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.file;
import static dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveFixtures.zip;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
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
private static final ObjectMapper JSON = JsonMapper.builder().build();


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
        baseUrl(), baseUrl(), JSON);
  }

  private HttpSkillHub hubWithSkillCap(long maxSkillBytes) {
    return new HttpSkillHub(
        new TestConfig(baseUrl(), ApprovalMode.HITL, Optional.empty(), publishDir.toString(), maxSkillBytes),
        store, baseUrl(), baseUrl(), JSON);
  }

  private HttpSkillHub hubWithArchiveCap(long archiveMaxBytes) {
    return new HttpSkillHub(
        new TestConfig(baseUrl(), ApprovalMode.HITL, Optional.empty(), publishDir.toString(), 262_144L,
            archiveMaxBytes),
        store, baseUrl(), baseUrl(), JSON);
  }

  private HttpSkillHub productionPolicyHub() {
    return new HttpSkillHub(
        new TestConfig(baseUrl(), ApprovalMode.HITL, Optional.empty(), publishDir.toString()), store,
        baseUrl(), baseUrl(), new OutboundPolicy(), JSON);
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

  @Test
  void install_refusesPlainHttpUnderTheProductionPolicy() {
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> productionPolicyHub().install(baseUrl() + "/skills/foo/SKILL.md"));

    assertTrue(refused.getMessage().contains("https"), refused.getMessage());
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void install_refusesLoopbackEvenWhenSpelledAsHttps() {
    assertThrows(IllegalArgumentException.class,
        () -> productionPolicyHub().install("https://127.0.0.1/skills/foo/SKILL.md"));
    server.verify(0, getRequestedFor(urlPathEqualTo("/skills/foo/SKILL.md")));
  }

  @Test
  void aRedirectToAPrivateAddressIsRefusedOnTheHop() {
    server.stubFor(get(urlPathEqualTo("/skills/bounce/SKILL.md")).willReturn(aResponse()
        .withStatus(302).withHeader("Location", "http://10.0.0.5/SKILL.md")));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> hub(Optional.empty()).install(baseUrl() + "/skills/bounce/SKILL.md"));

    assertTrue(refused.getMessage().contains("private"), refused.getMessage());
    assertTrue(store.saved.isEmpty());
    server.verify(1, getRequestedFor(urlPathEqualTo("/skills/bounce/SKILL.md")));
  }

  @Test
  void aRelativeRedirectToAnAllowedTargetIsFollowed() {
    server.stubFor(get(urlPathEqualTo("/skills/moved/SKILL.md")).willReturn(aResponse()
        .withStatus(301).withHeader("Location", "/skills/real/SKILL.md")));
    server.stubFor(get(urlPathEqualTo("/skills/real/SKILL.md")).willReturn(aResponse()
        .withStatus(200).withBody(skillMarkdown("real", "Moved here"))));

    Skill saved = hub(Optional.empty()).install(baseUrl() + "/skills/moved/SKILL.md");

    assertEquals("real", saved.name());
  }

  @Test
  void aRedirectLoopIsStoppedWithAReason() {
    server.stubFor(get(urlPathEqualTo("/skills/loop/SKILL.md")).willReturn(aResponse()
        .withStatus(302).withHeader("Location", "/skills/loop/SKILL.md")));

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install(baseUrl() + "/skills/loop/SKILL.md"));

    assertTrue(failure.getMessage().contains("redirect"), failure.getMessage());
  }

  @Test
  void aSkillMdOverTheSizeCapIsRefusedBeforeItIsSaved() {
    server.stubFor(get(urlPathEqualTo("/skills/huge/SKILL.md")).willReturn(aResponse()
        .withStatus(200).withBody(skillMarkdown("huge", "x".repeat(600)))));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> hubWithSkillCap(256).install(baseUrl() + "/skills/huge/SKILL.md"));

    assertTrue(refused.getMessage().contains("256"), refused.getMessage());
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void aNamedSourceIsNotAnInstallLocation() {
    assertThrows(IllegalArgumentException.class, () -> hub(Optional.empty()).install("triage"));
  }

  @Test
  void install_recordsWhereTheSkillCameFrom() {
    stubGitHubFile("acme/skills", "main", "skills/triage/SKILL.md", skillMarkdown("triage", "Triage"));

    hub(Optional.empty()).install("acme/skills/triage");

    SkillOrigin origin = store.origins.get("triage");
    assertEquals("acme/skills/triage", origin.source());
    assertNull(origin.candidate());
    assertTrue(origin.installedEpochMs() > 0);
  }

  @Test
  void discover_folderOfARepositoryListsEachSkillMd() {
    stubGitHubTree("acme/skills", "main", """
        {"truncated":false,"tree":[
          {"path":"README.md","type":"blob"},
          {"path":"skills/triage/SKILL.md","type":"blob"},
          {"path":"skills/tdd/SKILL.md","type":"blob"},
          {"path":"other/x/SKILL.md","type":"blob"}
        ]}""");

    List<SkillCandidate> found = hub(Optional.empty())
        .discover("https://github.com/acme/skills/tree/main/skills");

    assertEquals(List.of("triage", "tdd"), found.stream().map(SkillCandidate::name).toList());
    assertEquals(baseUrl() + "/acme/skills/main/skills/tdd/SKILL.md", found.get(1).location());
  }

  @Test
  void discover_repositoryRootSkillMdIsNamedAfterTheRepository() {
    stubGitHubTree("acme/solo", "main", """
        {"truncated":false,"tree":[{"path":"SKILL.md","type":"blob"}]}""");

    List<SkillCandidate> found = hub(Optional.empty()).discover("acme/solo");

    assertEquals(List.of("solo"), found.stream().map(SkillCandidate::name).toList());
  }

  @Test
  void installCandidate_savesTheFolderSkillAndRecordsItsSource() {
    stubGitHubTree("acme/skills", "main", """
        {"truncated":false,"tree":[{"path":"skills/tdd/SKILL.md","type":"blob"}]}""");
    stubGitHubFile("acme/skills", "main", "skills/tdd/SKILL.md", skillMarkdown("tdd", "Test first"));
    HttpSkillHub hub = hub(Optional.empty());
    SkillCandidate candidate = hub.discover("acme/skills").get(0);

    Skill saved = hub.installCandidate(candidate);

    assertEquals("tdd", saved.name());
    assertEquals("tdd", store.origins.get("tdd").candidate());
    assertEquals("acme/skills", store.origins.get("tdd").source());
  }

  @Test
  void discover_archiveListsTheSkillFoldersItHolds() throws Exception {
    server.stubFor(get(urlPathEqualTo("/bundle.zip")).willReturn(aResponse()
        .withStatus(200)
        .withBody(zip(file("bundle/triage/SKILL.md", skillMarkdown("triage", "Triage")),
            file("bundle/triage/scripts/run.sh", "rm -rf /"),
            file("bundle/tdd/SKILL.md", skillMarkdown("tdd", "Test first")))
            )));

    List<SkillCandidate> found = hub(Optional.empty()).discover(baseUrl() + "/bundle.zip");

    assertEquals(List.of("triage", "tdd"), found.stream().map(SkillCandidate::name).toList());
    assertTrue(found.get(0).content().contains("# triage"));
    server.verify(1, getRequestedFor(urlPathEqualTo("/bundle.zip")));
  }

  @Test
  void installOfAnArchiveHoldingSeveralSkillsFailsAndSaysSo() throws Exception {
    server.stubFor(get(urlPathEqualTo("/two.zip")).willReturn(aResponse()
        .withStatus(200)
        .withBody(zip(file("a/SKILL.md", skillMarkdown("a", "A")), file("b/SKILL.md", skillMarkdown("b", "B"))))));

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install(baseUrl() + "/two.zip"));

    assertTrue(failure.getMessage().contains("2 skills"), failure.getMessage());
    assertTrue(store.saved.isEmpty());
  }

  @Test
  void discoverUpload_listsTheSkillFoldersOfAnUploadedArchiveWithoutTouchingTheNetwork() throws Exception {
    List<SkillCandidate> found = hub(Optional.empty()).discoverUpload("bundle.zip", zip(
        file("bundle/triage/SKILL.md", skillMarkdown("triage", "Triage")),
        file("bundle/triage/scripts/run.sh", "rm -rf /"),
        file("bundle/tdd/SKILL.md", skillMarkdown("tdd", "Test first"))));

    assertEquals(List.of("triage", "tdd"), found.stream().map(SkillCandidate::name).toList());
    assertEquals("upload:bundle.zip", found.get(0).source());
    assertTrue(found.get(0).content().contains("# triage"));
    server.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void discoverUpload_readsAnUploadedSkillMdAsOneSkill() {
    List<SkillCandidate> found = hub(Optional.empty())
        .discoverUpload("SKILL.md", skillMarkdown("solo", "Alone").getBytes(StandardCharsets.UTF_8));

    assertEquals(List.of("solo"), found.stream().map(SkillCandidate::name).toList());
    assertEquals("upload:SKILL.md", found.get(0).source());
  }

  @Test
  void discoverUpload_refusesAnArchiveOverTheByteCapAsALimit() throws Exception {
    byte[] noise = new byte[6000];
    new Random(7).nextBytes(noise);
    byte[] archive = zip(file("bundle/triage/SKILL.md", skillMarkdown("triage", "Triage")),
        new ArchiveFixtures.Entry("bundle/triage/assets/blob.bin", noise));

    SkillLimitException refused = assertThrows(SkillLimitException.class,
        () -> hubWithArchiveCap(4096).discoverUpload("bundle.zip", archive));

    assertTrue(refused.getMessage().contains("4096"), refused.getMessage());
  }

  @Test
  void discoverUpload_refusesAnUploadedSkillMdOverTheSkillCap() {
    assertThrows(SkillLimitException.class, () -> hubWithSkillCap(64)
        .discoverUpload("SKILL.md", skillMarkdown("big", "x".repeat(200)).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void discover_gistReadsTheSkillMdOfTheGist() {
    server.stubFor(get(urlPathEqualTo("/gists/abc123")).willReturn(okJson("""
        {"files":{"SKILL.md":{"raw_url":"%s/gist-raw/SKILL.md"}}}""".formatted(baseUrl()))));
    server.stubFor(get(urlPathEqualTo("/gist-raw/SKILL.md")).willReturn(aResponse()
        .withStatus(200).withBody(skillMarkdown("gisted", "From a gist"))));

    List<SkillCandidate> found = hub(Optional.empty()).discover("https://gist.github.com/someone/abc123");
    Skill saved = hub(Optional.empty()).installCandidate(found.get(0));

    assertEquals("gisted", saved.name());
  }

  @Test
  void install_folderWithoutSkillMdNamesWhatWasTried() {
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> hub(Optional.empty()).install("https://github.com/acme/skills/tree/main/nothing"));

    assertTrue(failure.getMessage().contains("nothing/SKILL.md"), failure.getMessage());
  }

  private record TestConfig(String baseUrl, ApprovalMode approvalMode,
      Optional<List<String>> wellKnownHosts, String publishDir, long maxSkillBytes, long archiveMaxBytes)
      implements SkillHubConfig {

    TestConfig(String baseUrl, ApprovalMode approvalMode, Optional<List<String>> wellKnownHosts,
        String publishDir) {
      this(baseUrl, approvalMode, wellKnownHosts, publishDir, 262_144L, 10_485_760L);
    }

    TestConfig(String baseUrl, ApprovalMode approvalMode, Optional<List<String>> wellKnownHosts,
        String publishDir, long maxSkillBytes) {
      this(baseUrl, approvalMode, wellKnownHosts, publishDir, maxSkillBytes, 10_485_760L);
    }

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public int maxInstallsPerCall() {
      return 10;
    }

    @Override
    public Archive archive() {
      return new Archive() {
        @Override
        public long maxBytes() {
          return archiveMaxBytes;
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
