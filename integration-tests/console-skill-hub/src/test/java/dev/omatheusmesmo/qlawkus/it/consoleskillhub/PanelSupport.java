package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static io.restassured.RestAssured.given;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Shared fixtures for the install panel tests: credentials, SKILL.md text, zip archives and the hub calls the panel makes. */
final class PanelSupport {

  static final String USER = "qlawkus";
  static final String PASSWORD = "qlawkus-test";

  record Entry(String name, byte[] content) {
  }

  private PanelSupport() {
  }

  static String skillMd(String name, String description) {
    return "---\nname: " + name + "\ndescription: " + description + "\n---\n# " + name + "\nSteps.";
  }

  static Entry text(String name, String content) {
    return new Entry(name, content.getBytes(StandardCharsets.UTF_8));
  }

  static byte[] zip(Entry... entries) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (Entry entry : entries) {
        zip.putNextEntry(new ZipEntry(entry.name()));
        zip.write(entry.content());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  static byte[] incompressible(int size) {
    byte[] bytes = new byte[size];
    new Random(42).nextBytes(bytes);
    return bytes;
  }

  static void stubSkill(WireMock wiremock, String path, String markdown) {
    wiremock.register(WireMock.get(WireMock.urlEqualTo(path))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(markdown)));
  }

  static Response postJson(String path, String json) {
    return given().auth().preemptive().basic(USER, PASSWORD)
        .contentType("application/json").body(json)
        .post("/api/admin/skills/" + path);
  }

  static Response postText(String path, String text) {
    return given().auth().preemptive().basic(USER, PASSWORD)
        .contentType("text/plain").body(text)
        .post("/api/admin/skills/" + path);
  }

  static Response postUpload(String path, String fileName, byte[] content, String skills) {
    var request = given().auth().preemptive().basic(USER, PASSWORD)
        .multiPart("file", fileName, content, "application/octet-stream");
    if (skills != null) {
      request = request.multiPart("skills", skills);
    }
    return request.post("/api/admin/skills/" + path);
  }
}
