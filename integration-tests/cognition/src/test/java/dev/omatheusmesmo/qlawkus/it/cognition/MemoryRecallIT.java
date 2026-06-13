package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end memory test against the packaged (and native) artifact: the agent must persist a
 * fact through the real remember/search tools and embedding pipeline, then recall it. Gated on a
 * real LLM, mirroring {@link AgentServiceIT}; skipped in mock mode.
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils#usesLLM")
class MemoryRecallIT {

  private static final String HANDLE = "omatheusmesmo";

  @TestHTTPResource
  URI baseUri;

  private HttpClient client() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  private String auth() {
    String password = System.getProperty("test.qlawkus.password", "qlawkus-it");
    return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());
  }

  private HttpResponse<String> chat(String message) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUri + "api/chat/sync"))
        .header("Content-Type", "application/json")
        .header("Authorization", auth())
        .timeout(Duration.ofSeconds(120))
        .POST(HttpRequest.BodyPublishers.ofString("{\"message\":" + jsonString(message) + "}"))
        .build();
    return client().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String jsonString(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  @Test
  @Order(1)
  void cleanSlate_purgesAllMemory() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUri + "api/admin/memory?all=true"))
        .header("Authorization", auth())
        .DELETE()
        .build();

    HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(204, response.statusCode(), "purge-all should return 204");
  }

  @Test
  @Order(2)
  void agentPersistsFactToLongTermMemory() throws Exception {
    HttpResponse<String> chat = chat(
        "Please remember this for future conversations: my GitHub handle is " + HANDLE + ".");
    assertEquals(200, chat.statusCode(), "chat should return 200. Got: " + chat.body());

    HttpRequest summary = HttpRequest.newBuilder()
        .uri(URI.create(baseUri + "api/admin/memory"))
        .header("Authorization", auth())
        .GET()
        .build();
    HttpResponse<String> response = client().send(summary, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("remember-tool"),
        "Agent should have persisted the fact via RememberFactTool. Memory summary: " + response.body());
  }

  @Test
  @Order(3)
  void agentRecallsStoredFact() throws Exception {
    HttpResponse<String> response = chat("What is my GitHub handle? Reply with just the handle.");

    assertEquals(200, response.statusCode());
    assertTrue(response.body().toLowerCase().contains(HANDLE),
        "Agent should recall the stored handle. Got: " + response.body());
  }
}
