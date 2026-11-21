package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Turn ordering and interruption on the live socket, driven by fake speech adapters: a spoken utterance
 * becomes a transcript, then text, then audio in sentence order, then turn-done; a new utterance supersedes
 * the active turn and none of its audio reaches the browser afterwards.
 */
@QuarkusTest
@ConnectWireMock
class VoiceTurnsTest {

    private static final long SETTLE_MILLIS = 500;

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
        ScriptedSpeech.slowGate = null;
        VoiceTestSupport.stubEmbeddings(wiremock);
    }

    @AfterEach
    void releaseSpeech() {
        CountDownLatch gate = ScriptedSpeech.slowGate;
        ScriptedSpeech.slowGate = null;
        if (gate != null) {
            gate.countDown();
        }
    }

    @Test
    void aSpokenUtteranceIsTranscribedAnsweredAndSpokenInOrder() {
        VoiceTestSupport.stubAgentReply(wiremock, "First sentence. Second sentence.");
        ScriptedTranscriber.say("what time is it");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            List<JsonNode> events = client.snapshot();
            VoiceClient.assertOrdered(events, "transcript", "text");
            VoiceClient.assertOrdered(events, "text", "audio");
            VoiceClient.assertOrdered(events, "audio", "turn-done");
            assertEquals("what time is it", events.get(VoiceClient.indexOf(events, "transcript")).path("text").asText());
            assertEquals(List.of("speech:First sentence.", "speech:Second sentence."), spokenSentences(events));
            assertTrue(ScriptedTranscriber.mimeTypes().contains("audio/wav"), "utterances go to STT as WAV");
        }
    }

    @Test
    void consecutiveTurnsShareTheChatConversation() {
        VoiceTestSupport.stubAgentReply(wiremock, "Noted.");
        ScriptedTranscriber.say("my favourite colour is green");
        ScriptedTranscriber.say("what did I say my colour was");

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 2);
        }

        boolean secondTurnSeesFirst = wiremock.find(postRequestedFor(urlEqualTo("/api/chat"))).stream()
                .map(request -> request.getBodyAsString())
                .anyMatch(body -> body.contains("what did I say my colour was")
                        && body.contains("my favourite colour is green"));
        assertTrue(secondTurnSeesFirst, "the second turn is answered with the first turn in its history");
    }

    @Test
    void speakingAgainStopsTheObsoleteTurnAndItsAudioNeverArrives() throws InterruptedException {
        VoiceTestSupport.stubAgentReply(wiremock, "Slow answer. Later part.");
        ScriptedTranscriber.say("first question");
        ScriptedTranscriber.say("second question");
        CountDownLatch gate = new CountDownLatch(1);
        ScriptedSpeech.slowGate = gate;

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("text", event -> event.path("turn").asInt() == 1);

            client.speakUtterance(3200);
            Thread.sleep(SETTLE_MILLIS);
            gate.countDown();

            client.awaitTypeWhere("interrupted", event -> event.path("turn").asInt() == 1);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 2);

            client.assertAllOfTypeAbsent("audio", event -> event.path("turn").asInt() == 1);
            assertTrue(client.snapshot().stream().anyMatch(event -> "audio".equals(event.path("type").asText())
                    && event.path("turn").asInt() == 2), "the superseding turn is still spoken");
        }
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD));
    }

    private static List<String> spokenSentences(List<JsonNode> events) {
        return events.stream()
                .filter(event -> "audio".equals(event.path("type").asText()))
                .map(event -> new String(Base64.getDecoder().decode(event.path("data").asText()),
                        StandardCharsets.UTF_8))
                .toList();
    }
}
