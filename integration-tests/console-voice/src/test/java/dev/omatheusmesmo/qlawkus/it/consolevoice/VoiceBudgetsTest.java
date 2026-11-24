package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The budgets the server enforces, with the limits set in application.properties: a 4000 byte utterance, a
 * 2048 byte frame and two queued segments.
 */
@QuarkusTest
@ConnectWireMock
class VoiceBudgetsTest {

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
    void anUtteranceOverTheByteBudgetIsDroppedAndListeningStopsUntilTheOwnerSpeaksAgain() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"start\",\"sampleRate\":16000}");
            client.sendFrame(new byte[2000]);
            client.sendFrame(new byte[2000]);
            client.sendFrame(new byte[2000]);

            VoiceClient.assertCode(client.awaitType("error"), "utterance-too-long");
            client.sendFrame(new byte[64]);
            VoiceClient.assertCode(client.awaitTypeWhere("error",
                    event -> "not-listening".equals(event.path("code").asString())), "not-listening");
        }
    }

    @Test
    void aFrameOverTheFrameBudgetIsRefused() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"start\",\"sampleRate\":16000}");
            client.sendFrame(new byte[2050]);

            VoiceClient.assertCode(client.awaitType("error"), "frame-too-large");
        }
    }

    @Test
    void anEmptyUtteranceIsReportedWithoutAnAgentTurn() {
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.sendJson("{\"type\":\"start\",\"sampleRate\":16000}");
            client.sendJson("{\"type\":\"utterance-end\"}");

            VoiceClient.assertCode(client.awaitType("error"), "empty-utterance");
            assertTrue(wiremock.find(postRequestedFor(urlEqualTo("/api/chat"))).isEmpty());
        }
    }

    @Test
    void sentencesBeyondTheQueueBudgetAreNotSpoken() {
        VoiceTestSupport.stubAgentReply(wiremock, "Slow one. Two. Three. Four. Five.");
        ScriptedTranscriber.say("count to five");
        CountDownLatch gate = new CountDownLatch(1);
        ScriptedSpeech.slowGate = gate;

        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            VoiceClient.assertCode(client.awaitType("notice"), "speech-queue-full");
            gate.countDown();
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);

            long audio = client.snapshot().stream()
                    .filter(event -> "audio".equals(event.path("type").asString())).count();
            assertTrue(audio < 5, "the queue budget stops some of the five sentences from being spoken, got " + audio);
        }
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD));
    }
}
