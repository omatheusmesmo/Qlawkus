package dev.omatheusmesmo.qlawkus.it.smoke;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.service.tool.ToolProvider;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProviderSupplier;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
class SmokeTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @TestHTTPResource
    URI baseUri;

    @BeforeEach
    void setupStubs() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @Test
    void applicationStarts_withAgentService() {
        assertNotNull(agentService, "AgentService should be injectable with all extensions on classpath");
    }

    @Test
    void agentService_chatsWithRealLLM() {
        String response = agentService.chat("it-test", "Say exactly: pong")
                .collect().asList()
                .await().atMost(Duration.ofMinutes(5))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .reduce("", (a, b) -> a + b);

        assertThat(response, QlawkusTestUtils.containsStringOrMock("pong"));
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
        assertThat(httpResponse.body(), QlawkusTestUtils.containsStringOrMock("hello"));
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
