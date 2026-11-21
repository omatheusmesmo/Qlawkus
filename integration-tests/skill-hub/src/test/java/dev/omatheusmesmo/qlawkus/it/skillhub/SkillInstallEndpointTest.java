package dev.omatheusmesmo.qlawkus.it.skillhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The console's install endpoints, booted for real: authentication is required, a link installs one
 * skill, pasted text installs, a name is refused with a reason, and a folder or archive is listed and then
 * installed from a selection.
 */
@QuarkusTest
@ConnectWireMock
class SkillInstallEndpointTest {

  WireMock wiremock;

  @TestHTTPResource("/")
  URL app;

  @Inject
  SkillStore skillStore;

  @Inject
  SkillHubConfig config;

  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void stubRegistry() throws Exception {
    wiremock.resetToDefaultMappings();
    wiremock.register(WireMock.get(WireMock.urlEqualTo("/skills/bar/SKILL.md"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(
            "---\nname: bar-skill\ndescription: Does bar\n---\n# Bar\nSteps.")));
    wiremock.register(WireMock.get(WireMock.urlEqualTo("/bundle.zip"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(bundle())));
  }

  @Test
  void theEndpointRequiresAuthentication() throws Exception {
    HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint("install"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"source\":\"x/y\"}")).build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(401, response.statusCode());
  }

  @Test
  void aLinkInstallsOneSkill() throws Exception {
    HttpResponse<String> response = post("install", "{\"source\":\"" + wiremockUrl("/skills/bar/SKILL.md") + "\"}",
        "application/json");

    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("bar-skill"), response.body());
    assertTrue(skillStore.get("bar-skill").isPresent());
  }

  @Test
  void pastedTextInstalls() throws Exception {
    HttpResponse<String> response = post("install/text",
        "---\nname: pasted-skill\ndescription: From the console\n---\n# Pasted", "text/plain");

    assertEquals(200, response.statusCode(), response.body());
    assertTrue(skillStore.get("pasted-skill").isPresent());
  }

  @Test
  void aSkillNameIsRefusedWithAReason() throws Exception {
    HttpResponse<String> response = post("install", "{\"source\":\"triage\"}", "application/json");

    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("skill name"), response.body());
  }

  @Test
  void anArchiveIsListedThenInstalledFromASelection() throws Exception {
    String body = "{\"source\":\"" + wiremockUrl("/bundle.zip") + "\"}";
    HttpResponse<String> listed = post("install/list", body, "application/json");
    assertEquals(200, listed.statusCode(), listed.body());
    assertTrue(listed.body().contains("triage") && listed.body().contains("tdd"), listed.body());

    HttpResponse<String> installed = post("install/selection",
        "{\"source\":\"" + wiremockUrl("/bundle.zip") + "\",\"skills\":\"triage\"}", "application/json");

    assertEquals(200, installed.statusCode(), installed.body());
    assertTrue(skillStore.get("triage").isPresent());
    assertTrue(skillStore.get("tdd").isEmpty(), "only the selected skill is installed");
  }

  @Test
  void anUploadedArchiveIsListedThenInstalledFromASelectionAndRecordsTheUpload() throws Exception {
    HttpResponse<String> listed = upload("install/upload/list", "bundle.zip", bundle(), null);
    assertEquals(200, listed.statusCode(), listed.body());
    assertTrue(listed.body().contains("triage") && listed.body().contains("tdd"), listed.body());

    HttpResponse<String> installed = upload("install/upload/selection", "bundle.zip", bundle(), "triage");

    assertEquals(200, installed.statusCode(), installed.body());
    assertTrue(skillStore.get("triage").isPresent());
    assertTrue(skillStore.get("tdd").isEmpty(), "only the selected skill is installed");
    assertEquals("upload:bundle.zip", skillStore.originOf("triage").orElseThrow().source());
  }

  @Test
  void anUploadedSkillMdIsInstalledAsOneSkill() throws Exception {
    byte[] markdown = "---\nname: uploaded-skill\ndescription: From a file\n---\n# Uploaded".getBytes(StandardCharsets.UTF_8);

    HttpResponse<String> installed = upload("install/upload/selection", "SKILL.md", markdown, "uploaded-skill");

    assertEquals(200, installed.statusCode(), installed.body());
    assertTrue(skillStore.get("uploaded-skill").isPresent());
  }

  @Test
  void aLinkInstalledSkillIsUpdatedFromItsSource() throws Exception {
    post("install", "{\"source\":\"" + wiremockUrl("/skills/bar/SKILL.md") + "\"}", "application/json");
    wiremock.register(WireMock.get(WireMock.urlEqualTo("/skills/bar/SKILL.md"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(
            "---\nname: bar-skill\ndescription: Does bar, now better\n---\n# Bar\nNew steps.")));

    HttpResponse<String> updated = post("install/update/bar-skill", "", "application/json");

    assertEquals(204, updated.statusCode(), updated.body());
    assertEquals("Does bar, now better", skillStore.get("bar-skill").orElseThrow().description());
  }

  @Test
  void updateRefusesAnUploadedSkillAndAPastedOne() throws Exception {
    upload("install/upload/selection", "SKILL.md",
        "---\nname: filed\ndescription: Filed\n---\n# Filed".getBytes(StandardCharsets.UTF_8), "filed");
    post("install/text", "---\nname: pasted\ndescription: Pasted\n---\n# Pasted", "text/plain");

    HttpResponse<String> uploaded = post("install/update/filed", "", "application/json");
    HttpResponse<String> pasted = post("install/update/pasted", "", "application/json");

    assertEquals(400, uploaded.statusCode(), uploaded.body());
    assertTrue(uploaded.body().contains("uploaded file"), uploaded.body());
    assertEquals(400, pasted.statusCode(), pasted.body());
    assertTrue(pasted.body().contains("No recorded source"), pasted.body());
  }

  @Test
  void updateRequiresAuthentication() throws Exception {
    HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint("install/update/bar-skill"))
        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());

    assertEquals(401, response.statusCode());
  }

  @Test
  void aRefusalCarriesItsKindSoTheConsoleCanSayWhatHappened() throws Exception {
    HttpResponse<String> response = post("install", "{\"source\":\"triage\"}", "application/json");

    assertEquals(400, response.statusCode(), response.body());
    assertTrue(response.body().contains("\"kind\":\"refused\""), response.body());
  }

  @Test
  void anUnreachableSourceIsReportedAsUnreachable() throws Exception {
    HttpResponse<String> response = post("install", "{\"source\":\"http://localhost:1/none/SKILL.md\"}",
        "application/json");

    assertEquals(422, response.statusCode(), response.body());
    assertTrue(response.body().contains("\"kind\":\"unreachable\""), response.body());
  }

  private HttpResponse<String> upload(String path, String fileName, byte[] content, String skills) throws Exception {
    String boundary = "----qlawkus" + System.nanoTime();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(content);
    body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    if (skills != null) {
      body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"skills\"\r\n\r\n" + skills
          + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(HttpRequest.newBuilder(endpoint(path))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .header("Authorization", basicAuth())
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private String basicAuth() {
    return "Basic " + Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes(StandardCharsets.UTF_8));
  }

  private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
    return client.send(HttpRequest.newBuilder(endpoint(path))
        .header("Content-Type", contentType)
        .header("Authorization", "Basic " + Base64.getEncoder()
            .encodeToString("qlawkus:qlawkus-test".getBytes(StandardCharsets.UTF_8)))
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI endpoint(String path) throws Exception {
    return app.toURI().resolve("api/admin/skills/" + path);
  }

  private String wiremockUrl(String path) {
    return config.baseUrl() + path;
  }

  private static byte[] bundle() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("bundle/triage/SKILL.md"));
      zip.write("---\nname: triage\ndescription: Triage\n---\n# Triage".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("bundle/tdd/SKILL.md"));
      zip.write("---\nname: tdd\ndescription: Test first\n---\n# TDD".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return out.toByteArray();
  }
}
