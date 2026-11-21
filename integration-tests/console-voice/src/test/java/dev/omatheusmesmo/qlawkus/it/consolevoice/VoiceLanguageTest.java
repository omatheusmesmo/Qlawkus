package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The language flow end to end on the live socket: the recognizer's detected language comes back with each
 * transcript, an explicit choice is passed to recognition as a hint and forces the speech language, auto
 * mode speaks each reply in its own language, and a typed message runs a turn without speech-to-text.
 */
@QuarkusTest
@ConnectWireMock
class VoiceLanguageTest {

    @TestHTTPResource("/")
    URI base;

    @Inject
    BasicWebSocketConnector connector;

    WireMock wiremock;

    @BeforeEach
    void reset() {
        wiremock.resetMappings();
        wiremock.resetRequests();
        ScriptedTranscriber.SCRIPT.clear();
        ScriptedTranscriber.CALLS.clear();
        ScriptedSpeech.slowGate = null;
        VoiceTestSupport.stubEmbeddings(wiremock);
    }

    @AfterEach
    void forgetScript() {
        ScriptedTranscriber.SCRIPT.clear();
    }

    @Test
    void theReadyEventOffersAutoAndTheLanguagesThatCanBeSpoken() {
        try (VoiceClient client = open()) {
            JsonNode ready = client.awaitType("ready");

            assertEquals("auto", ready.path("language").asText());
            assertEquals("en", ready.path("defaultLanguage").asText());
            assertEquals(List.of("en", "pt", "es"), values(ready.path("languages")));
            assertEquals(2000, ready.path("maxTextChars").asInt());
        }
    }

    @Test
    void theDetectedLanguageComesBackWithTheTranscriptAndAutoSendsNoHint() {
        VoiceTestSupport.stubAgentReply(wiremock, "Certo.");
        ScriptedTranscriber.say("tudo bem com você", "pt");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            JsonNode transcript = client.awaitType("transcript");

            assertEquals("pt", transcript.path("language").asText());
            assertEquals("tudo bem com você", transcript.path("text").asText());
            assertNull(ScriptedTranscriber.CALLS.get(0).hint(), "auto mode leaves the recognizer to detect");
        }
    }

    @Test
    void aPortugueseReplyIsSpokenWithThePortugueseVoiceInAutoMode() {
        VoiceTestSupport.stubAgentReply(wiremock,
                "Claro, eu não sei, mas posso ajudar você com isso. Obrigado pela pergunta.");
        ScriptedTranscriber.say("me ajuda com uma coisa", "pt");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            List<JsonNode> audio = audioEvents(client);
            assertTrue(audio.size() >= 1, "the reply is spoken");
            audio.forEach(event -> assertEquals("pt", event.path("language").asText(), event.toString()));
        }
    }

    @Test
    void anEnglishReplyIsSpokenWithTheEnglishVoiceInAutoMode() {
        VoiceTestSupport.stubAgentReply(wiremock,
                "The weather is fine and you can see it with the window open.");
        ScriptedTranscriber.say("how is the weather", "en");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            List<JsonNode> audio = audioEvents(client);
            assertTrue(audio.size() >= 1, "the reply is spoken");
            audio.forEach(event -> assertEquals("en", event.path("language").asText(), event.toString()));
        }
    }

    @Test
    void theSessionLanguageCarriesAnUninformativeReplyWhenTheOwnerHasSettledOnOne() {
        VoiceTestSupport.stubAgentReply(wiremock, "Ok.");
        ScriptedTranscriber.say("oi, tudo bem", "pt");
        ScriptedTranscriber.say("obrigado pela ajuda", "pt");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            client.speakUtterance(3200);
            JsonNode second = client.awaitTypeWhere("transcript", event -> event.path("turn").asInt() == 2);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 2);

            assertEquals("pt", second.path("sessionLanguage").asText());
            audioEvents(client).stream()
                    .filter(event -> event.path("turn").asInt() == 2)
                    .forEach(event -> assertEquals("pt", event.path("language").asText(), event.toString()));
        }
    }

    @Test
    void anExplicitLanguageIsPassedAsTheRecognizerHintAndForcesTheSpeechLanguage() {
        VoiceTestSupport.stubAgentReply(wiremock, "The weather is fine and you can see it with the window open.");
        ScriptedTranscriber.say("how is the weather", "en");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"language\",\"language\":\"es\"}");
            JsonNode echoed = client.awaitType("language");
            assertEquals("es", echoed.path("language").asText());

            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            assertEquals("es", ScriptedTranscriber.CALLS.get(0).hint());
            List<JsonNode> audio = audioEvents(client);
            assertTrue(audio.size() >= 1, "the reply is spoken");
            audio.forEach(event -> assertEquals("es", event.path("language").asText(), event.toString()));
        }
    }

    @Test
    void autoCanBeChosenAgainAfterAnExplicitLanguage() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"language\",\"language\":\"pt\"}");
            client.awaitType("language");
            client.sendJson("{\"type\":\"language\",\"language\":\"auto\"}");
            JsonNode echoed = client.awaitType("language");

            assertEquals("auto", echoed.path("language").asText());
        }
    }

    @Test
    void aLanguageWithoutASpeechProviderIsRefused() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"language\",\"language\":\"de\"}");

            VoiceClient.assertCode(client.awaitType("error"), "unknown-language");
        }
    }

    @Test
    void aTypedMessageRunsATurnWithoutSpeechToText() {
        VoiceTestSupport.stubAgentReply(wiremock, "Noted.");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"text\",\"text\":\"remember the meeting is on friday\"}");
            JsonNode transcript = client.awaitType("transcript");
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            assertEquals("remember the meeting is on friday", transcript.path("text").asText());
            assertTrue(transcript.path("typed").asBoolean());
            assertTrue(ScriptedTranscriber.CALLS.isEmpty(), "a typed message never reaches speech-to-text");
            assertTrue(wiremock.find(postRequestedFor(urlEqualTo("/api/chat")))
                    .stream().anyMatch(request -> request.getBodyAsString()
                            .contains("remember the meeting is on friday")));
        }
    }

    @Test
    void anEmptyOrOverlongTypedMessageIsRefused() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"text\",\"text\":\"   \"}");
            VoiceClient.assertCode(client.awaitType("error"), "empty-text");

            client.sendJson("{\"type\":\"text\",\"text\":\"" + "x".repeat(2001) + "\"}");
            VoiceClient.assertCode(client.awaitTypeWhere("error",
                    event -> "text-too-long".equals(event.path("code").asText())), "text-too-long");
        }
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD));
    }

    private static List<JsonNode> audioEvents(VoiceClient client) {
        return client.snapshot().stream()
                .filter(event -> "audio".equals(event.path("type").asText()))
                .toList();
    }

    private static List<String> values(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
