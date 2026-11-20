package dev.omatheusmesmo.qlawkus.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.StreamedContextProbe;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that a REST turn, synchronous or streamed, runs its tools with the conversation it belongs to
 * and the {@code rest} provider with no chat, which is what a scheduled task needs to know where it
 * came from. The probe tool is the one the model calls to report what it saw.
 */
@QuarkusTest
@ConnectWireMock
class RestDeliveryContextTest {

    private static final String TS = "2026-04-25T00:00:00Z";

    WireMock wiremock;

    @Inject
    AgentStream agentStream;

    @Inject
    AgentDeliveryContext delivery;

    private String token;

    @BeforeEach
    void stubAProbeToolTurn() {
        wiremock.resetMappings();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed"))
                .willReturn(WireMock.okJson(embedJson())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings"))
                .willReturn(WireMock.okJson(openAiEmbeddingJson())));
        token = "probe-" + UUID.randomUUID();
        StreamedContextProbe.DELIVERY_SEEN.clear();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(10)
                .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
                        .withBody(textNdjson("unrelated"))));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(2)
                .withRequestBody(WireMock.containing(token))
                .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
                        .withBody(probeToolCallNdjson())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(1)
                .withRequestBody(WireMock.containing(token))
                .withRequestBody(WireMock.matching("(?s).*" + Pattern.quote(token) + ".*\"role\"\\s*:\\s*\"tool\".*"))
                .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
                        .withBody(textNdjson("probed"))));
    }

    @AfterEach
    void leaveNoCallerContext() {
        delivery.clear();
    }

    @Test
    void aSynchronousRestTurnRunsItsToolWithTheRestConversation() {
        given().auth().basic("qlawkus", "qlawkus-test")
                .contentType("application/json")
                .body("{\"message\": \"probe the delivery " + token + "\"}")
                .when().post("/api/chat/sync")
                .then().statusCode(200);

        StreamedContextProbe.DeliveryObservation seen = lastSeen();
        assertTrue(seen.memoryId() != null && List.of(ConversationId.REST, ConversationId.SHARED).contains(seen.memoryId()),
                seen.toString());
        assertEquals("rest", seen.providerId());
        assertNull(seen.chatId(), "a REST origin has no chat, so a task must name its destination");
    }

    @Test
    void aStreamedRestTurnRunsItsToolWithTheRestConversation() {
        given().auth().basic("qlawkus", "qlawkus-test")
                .contentType("application/json").accept("text/event-stream")
                .body("{\"message\": \"probe the delivery " + token + "\"}")
                .when().post("/api/chat")
                .then().statusCode(200);

        StreamedContextProbe.DeliveryObservation seen = lastSeen();
        assertTrue(seen.memoryId() != null && List.of(ConversationId.REST, ConversationId.SHARED).contains(seen.memoryId()),
                seen.toString());
        assertEquals("rest", seen.providerId());
        assertNull(seen.chatId());
    }

    @Test
    void aStreamOpenedWithAnOriginRunsItsToolWithThatOriginAndLeavesTheCallerClean() {
        String conversation = ConversationId.REST;
        delivery.clear();

        agentStream.stream(conversation, "probe the delivery " + token,
                        new AgentDeliveryContext.Snapshot(conversation, "rest", null))
                .collect().asList().await().indefinitely();

        assertFalse(delivery.hasDeliveryInfo());
        assertNull(delivery.memoryId(), "the caller's thread is left as it was found");
        StreamedContextProbe.DeliveryObservation seen = lastSeen();
        assertEquals(conversation, seen.memoryId(), "the tool sees the conversation the stream was opened for");
        assertEquals("rest", seen.providerId());
    }

    private static StreamedContextProbe.DeliveryObservation lastSeen() {
        List<StreamedContextProbe.DeliveryObservation> seen = StreamedContextProbe.DELIVERY_SEEN;
        assertFalse(seen.isEmpty(), "the probe tool ran");
        return seen.get(seen.size() - 1);
    }

    private static String probeToolCallNdjson() {
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"probeDelivery\",\"arguments\":{}}}]},"
                + "\"done\":false}\n"
                + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS
                + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":5,\"eval_count\":3}\n";
    }

    private static String textNdjson(String text) {
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + text + "\"},\"done\":false}\n"
                + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS
                + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":9,\"eval_count\":4}\n";
    }

    private static String embedJson() {
        return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}";
    }

    private static String openAiEmbeddingJson() {
        return "{\"data\":[{\"embedding\":" + vector(1024) + ",\"index\":0}],"
                + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }

    private static String vector(int size) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            embedding.append(i > 0 ? "," : "").append("0.01");
        }
        return embedding.append("]").toString();
    }
}
