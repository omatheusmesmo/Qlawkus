package dev.omatheusmesmo.qlawkus.it;

import dev.langchain4j.service.tool.ToolProvider;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProviderSupplier;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SmokeTest {

  @Inject
  AgentService agentService;

  @TestHTTPResource
  URI baseUri;

  @Test
  void applicationStarts_withAgentService() {
    assertNotNull(agentService, "AgentService should be injectable with all extensions on classpath");
  }

  @Test
  void agentService_chatsWithRealLLM() {
    String response = agentService.chat("Say exactly: pong")
      .collect().asList()
      .await().indefinitely()
      .stream()
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .reduce("", (a, b) -> a + b);

    assertFalse(response.isBlank(), "AgentService should return a non-blank response from LLM");
  }

  @Test
  void apiChatEndpoint_streamsSseFromRealLLM() throws Exception {
    String auth = Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes());

    HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(baseUri + "api/chat"))
      .header("Content-Type", "application/json")
      .header("Authorization", "Basic " + auth)
      .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Say exactly: hello\"}"))
      .build();

    HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, httpResponse.statusCode(),
      "SSE endpoint should return 200");
    assertFalse(httpResponse.body().isBlank(),
      "SSE response should contain LLM output");
  }

  @Test
  void clawTool_beansAreDiscoveredByArc() {
    Instance<Object> clawToolBeans = Arc.container().select(Object.class, new ClawToolLiteral());

    assertFalse(clawToolBeans.isUnsatisfied(),
      "@ClawTool beans should be satisfied with SampleExtensionTool on classpath");

    boolean found = false;
    for (Object tool : clawToolBeans) {
      if (tool instanceof SampleExtensionTool) {
        found = true;
        break;
      }
    }
    assertTrue(found, "SampleExtensionTool should be discovered via @ClawTool qualifier");
  }

  @SuppressWarnings("serial")
  static class ClawToolLiteral extends AnnotationLiteral<ClawTool> implements ClawTool {
  }

  @Test
  void clawToolProviderSupplier_resolvesFromArc() {
    ClawToolProviderSupplier supplier = new ClawToolProviderSupplier();

    ToolProvider resolved = supplier.get();
    assertNotNull(resolved);
    assertInstanceOf(ClawToolProvider.class, resolved);
  }

  @Test
  void clawToolProvider_providesExtensionTools() {
    ClawToolProvider provider = Arc.container().instance(ClawToolProvider.class).get();

    var result = provider.provideTools(null);

    assertNotNull(result, "ClawToolProvider should return a non-null ToolProviderResult");
    assertFalse(result.tools().isEmpty(),
      "ClawToolProvider should discover at least one extension tool");
  }
}
