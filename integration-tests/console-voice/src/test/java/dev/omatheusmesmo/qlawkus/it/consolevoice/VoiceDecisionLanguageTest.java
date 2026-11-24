package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
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
                    .filter(event -> event.path("type").asString().equals("transcript"))
                    .findFirst().orElseThrow().path("text").asString());
        }
        var requests = wiremock.find(postRequestedFor(urlEqualTo(DECISION_PATH)).withRequestBody(containing("LANGUAGE_DECISION")));
        assertEquals(1, requests.size());
        JsonNode request = JsonMapper.builder().build().readTree(requests.getFirst().getBodyAsString());
        assertEquals("gemma4:e2b", request.path("model").asString());
        assertEquals(2, request.path("messages").size(), "classification has no chat history or tools");
        assertTrue(!request.hasNonNull("tools") || request.path("tools").isEmpty());
        JsonNode input = JsonMapper.builder().build().readTree(request.path("messages").get(1).path("content").asString());
        assertEquals("bom dia como ta o projeto", input.path("utterance").asString());
    }

    @Test
    void switchingToEnglishUsesOneLanguageForTheModelAndAudioDespitePortugueseHistory() throws Exception {
        String englishRequest = "What was the last joke you heard?";
        decisionReply("pt");
        ScriptedTranscriber.say("Tudo bem com você?", "pt");
        ScriptedTranscriber.say(englishRequest, "en");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            decisionReply("en");
            VoiceTestSupport.stubAgentReply(wiremock, "I heard a joke about a computer.");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 2);
            var audio = client.snapshot().stream()
                    .filter(event -> "audio".equals(event.path("type").asString()) && event.path("turn").asInt() == 2)
                    .toList();
            assertFalse(audio.isEmpty());
            audio.forEach(event -> assertEquals("en", event.path("language").asString()));
        }
        var requests = wiremock.find(postRequestedFor(urlEqualTo(DECISION_PATH))
                .withRequestBody(containing(englishRequest))
                .withRequestBody(notMatching("(?s).*(LANGUAGE_DECISION|VOICE_REPLY_LANGUAGE_CHECK).*")));
        assertEquals(1, requests.size());
        JsonNode messages = JsonMapper.builder().build().readTree(requests.getFirst().getBodyAsString()).path("messages");
        assertTrue(messages.get(0).path("content").asString().contains("Write the entire reply in English (en)"));
        assertTrue(messages.toString().contains("Certo."), "the prior Portuguese reply must still be in context");
    }

    @Test
    void aPortugueseDraftForAnEnglishTurnIsCorrectedBeforeTextAndSpeech() throws Exception {
        String portuguese = "Estou ótimo. E você?";
        String english = "I'm great. How about you?";
        decisionReply("en");
        VoiceTestSupport.stubAgentReply(wiremock, portuguese);
        VoiceTestSupport.stubLanguageCheck(wiremock, "**I'm great.** _How about you?_", 1);
        ScriptedTranscriber.say("Hey, how are you today?", "en");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            var events = client.snapshot();
            String displayed = events.stream().filter(event -> "text".equals(event.path("type").asString()))
                    .map(event -> event.path("chunk").asString()).collect(Collectors.joining());
            assertEquals(english, displayed);
            var audio = events.stream().filter(event -> "audio".equals(event.path("type").asString())).toList();
            assertEquals(2, audio.size());
            audio.forEach(event -> assertEquals("en", event.path("language").asString()));
            assertEquals("speech:I'm great.", new String(Base64.getDecoder()
                    .decode(audio.getFirst().path("data").asString()), StandardCharsets.UTF_8));
        }
        var checks = wiremock.find(postRequestedFor(urlEqualTo(DECISION_PATH))
                .withRequestBody(containing("VOICE_REPLY_LANGUAGE_CHECK")));
        assertEquals(1, checks.size());
        JsonNode request = JsonMapper.builder().build().readTree(checks.getFirst().getBodyAsString());
        assertEquals(2, request.path("messages").size());
        assertFalse(request.hasNonNull("tools") && !request.path("tools").isEmpty());
        assertTrue(checks.getFirst().getBodyAsString().contains("English (en)"));
        assertTrue(checks.getFirst().getBodyAsString().contains(portuguese));
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
                        """.formatted(JsonMapper.builder().build().valueToTree(code).toString()))));
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(VoiceTestSupport.USER, VoiceTestSupport.PASSWORD));
    }

    private static void assertPortugueseAudio(VoiceClient client) {
        var audio = client.snapshot().stream().filter(event -> event.path("type").asString().equals("audio")).toList();
        assertFalse(audio.isEmpty(), "the turn must remain speakable");
        audio.forEach(event -> assertEquals("pt", event.path("language").asString()));
    }
}
