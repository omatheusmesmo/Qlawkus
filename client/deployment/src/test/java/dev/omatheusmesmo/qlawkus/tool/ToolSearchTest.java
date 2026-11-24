package dev.omatheusmesmo.qlawkus.tool;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@QuarkusTest
@ConnectWireMock
@TestProfile(ToolSearchTest.SearchEnabled.class)
class ToolSearchTest {

    public static class SearchEnabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "qlawkus.agent.tool-search.enabled", "true",
                    "qlawkus.agent.active-memory.enabled", "false",
                    "qlawkus.agent.transcript-archive.enabled", "false",
                    "qlawkus.agent.semantic-extractor.enabled", "false",
                    "qlawkus.skills.extractor.enabled", "false");
        }
    }

    WireMock wiremock;

    @Inject
    AgentService service;

    @Inject
    AgentRunner runner;

    @InjectMock
    EmbeddingModel embeddings;

    @Inject
    Instance<ToolSearchStrategy> globalStrategies;

    @Inject
    ObjectMapper mapper;

    @BeforeEach
    void stubSearchThenExecute() {
        when(embeddings.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream().map(segment -> Embedding.from(
                    segment.text().contains("runCommand") || segment.text().equals("execute shell command")
                            ? new float[] {1, 0} : new float[] {0, 1})).toList());
        });
        wiremock.resetMappings();
        wiremock.resetRequests();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .inScenario("tool-search").whenScenarioStateIs("Started")
                .willReturn(WireMock.okJson(reply("tool_search_tool", "{\"query\":\"execute shell command\"}")))
                .willSetStateTo("discovered"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .inScenario("tool-search").whenScenarioStateIs("discovered")
                .willReturn(WireMock.okJson(reply("runCommand", "{\"command\":\"echo search-ok\"}")))
                .willSetStateTo("executed"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .inScenario("tool-search").whenScenarioStateIs("executed")
                .willReturn(WireMock.okJson(reply(null, null))));
    }

    @Test
    void syncServiceDiscoversAndExecutesAnExtensionTool() throws Exception {
        assertEquals("done", service.chatSync("search-" + UUID.randomUUID(), "execute shell command"));
        assertSearchRoundTrip();
    }

    @Test
    void streamingServiceDiscoversAndExecutesAnExtensionTool() throws Exception {
        var events = service.chatEvents("search-" + UUID.randomUUID(), "execute shell command")
                .collect().asList().await().atMost(Duration.ofSeconds(30));
        assertFalse(events.isEmpty());
        assertSearchRoundTrip();
    }

    @Test
    void messagingRetainsItsCatalogUntilQuarkusPropagatesTheSearchSupplier() throws Exception {
        wiremock.resetMappings();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .willReturn(WireMock.okJson(reply(null, null))));
        assertEquals("done", runner.chat("search-" + UUID.randomUUID(), "execute shell command"));
        var requests = wiremock.find(WireMock.postRequestedFor(WireMock.urlEqualTo("/api/chat")));
        assertEquals(1, requests.size());
        JsonNode tools = mapper.readTree(requests.getFirst().getBodyAsString()).path("tools");
        assertTrue(tools.size() > 1);
        for (JsonNode tool : tools) {
            assertNotEquals("tool_search_tool", tool.path("function").path("name").asString());
        }
    }

    private void assertSearchRoundTrip() throws Exception {
        assertTrue(globalStrategies.isUnsatisfied(), "no auto-detected strategy on unrelated AI services");
        var requests = wiremock.find(WireMock.postRequestedFor(WireMock.urlEqualTo("/api/chat")));
        assertEquals(3, requests.size());
        List<JsonNode> bodies = requests.stream().map(request -> {
            try {
                return mapper.readTree(request.getBodyAsString());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).toList();
        JsonNode initial = bodies.stream().filter(body -> {
            for (JsonNode message : body.path("messages")) {
                if ("tool".equals(message.path("role").asString())) return false;
            }
            return true;
        }).findFirst().orElseThrow();
        assertEquals(1, initial.path("tools").size(), initial.toPrettyString());
        assertEquals("tool_search_tool", initial.path("tools").get(0).path("function").path("name").asString());
        assertTrue(bodies.stream().anyMatch(body -> {
            for (JsonNode tool : body.path("tools")) {
                if ("runCommand".equals(tool.path("function").path("name").asString())) return true;
            }
            return false;
        }), "the discovered extension tool must become available on subsequent requests");
        assertTrue(bodies.stream().anyMatch(body -> {
            for (JsonNode message : body.path("messages")) {
                if ("tool".equals(message.path("role").asString())
                        && message.path("content").asString().contains("search-ok")) return true;
            }
            return false;
        }), "the discovered tool must actually execute");
    }

    private static String reply(String tool, String arguments) {
        String message = tool == null ? "\"content\":\"done\"" :
                "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"" + tool
                        + "\",\"arguments\":" + arguments + "}}]";
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"2026-11-19T00:00:00Z\",\"message\":{\"role\":\"assistant\","
                + message + "},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":5,\"eval_count\":3}";
    }
}
