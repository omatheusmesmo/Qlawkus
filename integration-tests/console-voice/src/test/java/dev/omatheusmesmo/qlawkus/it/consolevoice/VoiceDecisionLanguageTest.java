package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the registered model chain and live voice socket against local model fixtures. */
@QuarkusTest
@ConnectWireMock
class VoiceDecisionLanguageTest {
    private static final String DECISION_PATH = "/api/chat";
    @TestHTTPResource("/") URI base;
    @Inject BasicWebSocketConnector connector;
    WireMock wiremock;

    @BeforeEach
    void reset() {
        wiremock.resetMappings();
        wiremock.resetRequests();
        ScriptedTranscriber.SCRIPT.clear();
        VoiceTestSupport.stubEmbeddings(wiremock);
        VoiceTestSupport.stubAgentReply(wiremock, "Certo.");
    }

    @Test
    void theDecisionSelectsPortugueseOnTheFirstUtteranceAndRunsOnlyOnce() throws Exception {
        decisionReply("{\"language\":\"pt\"}");
        ScriptedTranscriber.say("bom dia como ta o projeto", "es");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            assertPortugueseAudio(client);
            assertEquals("bom dia como ta o projeto", client.snapshot().stream()
                    .filter(event -> event.path("type").asText().equals("transcript"))
                    .findFirst().orElseThrow().path("text").asText());
        }
        var requests = wiremock.find(postRequestedFor(urlEqualTo(DECISION_PATH)).withRequestBody(containing("LANGUAGE_DECISION")));
        assertEquals(1, requests.size());
        JsonNode request = new ObjectMapper().readTree(requests.getFirst().getBodyAsString());
        assertEquals("gemma4:e2b", request.path("model").asText());
        assertEquals(2, request.path("messages").size(), "classification has no chat history or tools");
        assertTrue(!request.hasNonNull("tools") || request.path("tools").isEmpty());
        JsonNode input = new ObjectMapper().readTree(request.path("messages").get(1).path("content").asText());
        assertEquals("bom dia como ta o projeto", input.path("utterance").asText());
    }

    @Test
    void aManualPortugueseSelectionDoesNotCallTheDecisionModel() {
        ScriptedTranscriber.say("how is the weather", "en");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"language\",\"language\":\"pt\"}");
            client.awaitType("language");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            assertPortugueseAudio(client);
        }
        wiremock.verifyThat(0, postRequestedFor(urlEqualTo(DECISION_PATH)).withRequestBody(containing("LANGUAGE_DECISION")));
    }

    @Test
    void anInvalidDecisionRetainsPortugueseFromTheRecognizer() {
        decisionReply("not-a-language");
        ScriptedTranscriber.say("Oi.", "pt");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            assertPortugueseAudio(client);
        }
    }

    private void decisionReply(String code) {
        wiremock.register(post(urlEqualTo(DECISION_PATH))
                .withRequestBody(containing("LANGUAGE_DECISION"))
                .willReturn(okJson("""
                        {"model":"gemma4:e2b","created_at":"2026-11-21T00:00:00Z",
                         "message":{"role":"assistant","content":%s},"done":true,
                         "prompt_eval_count":10,"eval_count":1}
                        """.formatted(new ObjectMapper().valueToTree(code).toString()))));
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(VoiceTestSupport.USER, VoiceTestSupport.PASSWORD));
    }

    private static void assertPortugueseAudio(VoiceClient client) {
        var audio = client.snapshot().stream().filter(event -> event.path("type").asText().equals("audio")).toList();
        assertFalse(audio.isEmpty(), "the turn must remain speakable");
        audio.forEach(event -> assertEquals("pt", event.path("language").asText()));
    }
}
