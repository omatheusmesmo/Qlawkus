package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
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
import java.util.stream.Collectors;
import java.time.Duration;
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

    @Inject
    WorkingMemoryStore memory;

    @Inject
    AgentStream agent;

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
            assertEquals("what time is it", events.get(VoiceClient.indexOf(events, "transcript")).path("text").asString());
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
    void voiceTranscriptAndSpeechUseTheSamePlainTextWithPauses() {
        String markdown = """
                1. **Check the settings** 🔧
                2. [Read the guide](https://example.org)
                """;
        VoiceTestSupport.stubAgentReply(wiremock, markdown);
        ScriptedTranscriber.say("formatting check owner text");
        try (VoiceClient client = open()) {
            client.awaitType("ready");
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
            String displayed = client.snapshot().stream().filter(event -> "text".equals(event.path("type").asString()))
                    .map(event -> event.path("chunk").asString()).collect(Collectors.joining());
            assertEquals("Check the settings. Read the guide.", displayed);
            assertEquals(List.of("speech:Check the settings.", "speech:Read the guide."), spokenSentences(client.snapshot()));
        }
    }

    @Test
    void spokenPresentationIsInTheSystemPromptAndLeavesTheOwnerMessageAndNextTextTurnUnchanged() throws Exception {
        String ownerText = "keep this owner message unchanged";
        VoiceTestSupport.stubAgentReply(wiremock, "Noted.");
        ScriptedTranscriber.say(ownerText);
        String conversation;
        try (VoiceClient client = open()) {
            conversation = client.awaitType("ready").path("conversation").asString();
            client.speakUtterance(3200);
            client.awaitTypeWhere("turn-done", event -> event.path("turn").asInt() == 1);
        }
        var requests = wiremock.find(postRequestedFor(urlEqualTo("/api/chat")));
        assertTrue(requests.stream().anyMatch(request -> request.getBodyAsString().contains("Spoken conversation")));
        assertTrue(memory.getMessages(conversation).stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).anyMatch(message -> message.singleText().equals(ownerText)));
        String regularMessage = "ordinary text turn after spoken turn";
        agent.stream("voice-style-reset-check", regularMessage).collect().asList().await().atMost(Duration.ofSeconds(15));
        ObjectMapper json = JsonMapper.builder().build();
        boolean ordinaryRequestSeen = false;
        for (var request : wiremock.find(postRequestedFor(urlEqualTo("/api/chat")))) {
            JsonNode messages = json.readTree(request.getBodyAsString()).path("messages");
            boolean ordinary = false;
            for (JsonNode message : messages) {
                if ("user".equals(message.path("role").asString()) && regularMessage.equals(message.path("content").asString())) ordinary = true;
            }
            if (ordinary) {
                ordinaryRequestSeen = true;
                for (JsonNode message : messages) {
                    if ("system".equals(message.path("role").asString())) {
                        assertTrue(!message.path("content").asString().contains("Spoken conversation"),
                                "speech presentation must not leak into an ordinary turn on the same worker");
                    }
                }
            }
        }
        assertTrue(ordinaryRequestSeen, "the ordinary turn must reach the model before checking its prompt");
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
            assertTrue(client.snapshot().stream().anyMatch(event -> "audio".equals(event.path("type").asString())
                    && event.path("turn").asInt() == 2), "the superseding turn is still spoken");
        }
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD));
    }

    private static List<String> spokenSentences(List<JsonNode> events) {
        return events.stream()
                .filter(event -> "audio".equals(event.path("type").asString()))
                .map(event -> new String(Base64.getDecoder().decode(event.path("data").asString()),
                        StandardCharsets.UTF_8))
                .toList();
    }
}
