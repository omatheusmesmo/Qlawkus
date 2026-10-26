package dev.omatheusmesmo.qlawkus.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.cognition.VoiceResponsePreference;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that a tool call on the streaming path runs with the caller's context: the delivery values,
 * and the request context the caller had active (or a fresh one when it had none). Tools run on the
 * provider's callback thread, so without the bridge none of this would reach them.
 */
@QuarkusTest
@ConnectWireMock
class StreamedToolContextTest {

    private static final String CHAT_ID = "chat-42";

    @Inject
    AgentStream stream;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    VoiceResponsePreference voice;

    @Inject
    StreamedToolContext streamedTools;

    WireMock wiremock;

    private String marker;

    @BeforeEach
    void stubEmbeddingsAndUnrelatedTurns() {
        wiremock.resetMappings();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed"))
            .willReturn(WireMock.okJson(embedJson())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings"))
            .willReturn(WireMock.okJson(openAiEmbeddingJson())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(10)
            .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(textNdjson("unrelated"))));
        StreamedContextProbe.SEEN.clear();
        marker = "probe-" + UUID.randomUUID();
    }

    @AfterEach
    void releaseCallerContext() {
        delivery.clear();
        if (Arc.container().requestContext().isActive()) {
            Arc.container().requestContext().terminate();
        }
    }

    /**
     * REST, console and messaging can share one conversation id. A synchronous caller that already has
     * its own context, a messaging turn, must keep it while a streamed turn on the same conversation is
     * open, or its tools would lose the chat they deliver to.
     */
    @Test
    void aCallerThatHasItsOwnContextKeepsItWhileAStreamOnTheSameConversationIsOpen() {
        String shared = conversation();
        Arc.container().requestContext().activate();
        delivery.set(shared, "rest", null);
        streamedTools.capture(shared);
        delivery.set(shared, "telegram", "chat-99");
        try (StreamedToolContext.Entered ignored = streamedTools.enter(shared)) {
            assertEquals("chat-99", delivery.chatId(), "the synchronous caller's delivery context is kept");
        } finally {
            streamedTools.release(shared);
        }
        assertEquals("chat-99", delivery.chatId());
    }

    @Test
    void aStreamedToolSeesTheCallersDeliveryAndRequestContext() {
        stubToolTurn("probeContext", "{}");
        Arc.container().requestContext().activate();
        delivery.set("memory-1", "telegram", CHAT_ID);
        voice.request("en");

        stream.stream(conversation(), "probe the context " + marker).collect().asList().await().indefinitely();

        List<StreamedContextProbe.Observation> seen = StreamedContextProbe.SEEN;
        assertEquals(1, seen.size(), "the probe tool ran once: " + seen);
        StreamedContextProbe.Observation observed = seen.get(0);
        assertEquals(CHAT_ID, observed.chatId(), "the tool sees the caller's delivery chat id");
        assertTrue(observed.requestActive(), "the tool runs inside a request context");
        assertTrue(observed.voiceRequested(), "the tool sees the caller's request-scoped state");
    }

    @Test
    void aVoiceRequestMadeByAStreamedToolReachesTheCaller() {
        stubToolTurn("requestPortugueseVoice", "{}");
        Arc.container().requestContext().activate();

        stream.stream(conversation(), "reply in portuguese " + marker).collect().asList().await().indefinitely();

        assertTrue(voice.isRequested(), "the caller's preference reflects the request the tool made");
        assertEquals("pt", voice.language());
    }

    @Test
    void respondWithVoiceRunsOnTheStreamingPath() {
        stubToolTurn("respondWithVoice", "{\"language\":\"en\"}");
        Arc.container().requestContext().activate();

        List<AgentEvent> events = stream.stream(conversation(), "send audio " + marker)
            .collect().asList().await().indefinitely();

        AgentEvent.ToolEnd end = events.stream()
            .filter(AgentEvent.ToolEnd.class::isInstance)
            .map(AgentEvent.ToolEnd.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("the model's tool call produced no tool-end: " + events));
        assertEquals("respondWithVoice", end.name());
        assertTrue(end.ok(), "the tool must not fail with ContextNotActiveException on the stream: " + events);
        assertTrue(voice.isRequested());
        assertEquals("en", voice.language());
    }

    @Test
    void aStreamWithoutACallerRequestContextGivesEachToolItsOwnAndLeavesNoneBehind() {
        stubToolTurn("probeContext", "{}");
        Arc.container().requestContext().terminate();
        assertFalse(Arc.container().requestContext().isActive(), "the caller starts with no request context");
        delivery.set("memory-2", "telegram", CHAT_ID);

        stream.stream(conversation(), "probe the context " + marker).collect().asList().await().indefinitely();

        StreamedContextProbe.Observation observed = StreamedContextProbe.SEEN.get(0);
        assertEquals(CHAT_ID, observed.chatId());
        assertTrue(observed.requestActive(), "a tool still gets a request context to use @RequestScoped beans");
        assertFalse(Arc.container().requestContext().isActive(),
            "the context the tool used is not left active on the caller");
    }

    private String conversation() {
        return "streamed-" + UUID.randomUUID();
    }

    private void stubToolTurn(String toolName, String argumentsJson) {
        wiremock.register(post(urlEqualTo("/api/chat")).atPriority(2)
            .withRequestBody(containing(marker))
            .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(toolCallNdjson(toolName, argumentsJson))));
        wiremock.register(post(urlEqualTo("/api/chat")).atPriority(1)
            .withRequestBody(containing(marker))
            .withRequestBody(matching("(?s).*" + Pattern.quote(marker) + ".*\"role\"\\s*:\\s*\"tool\".*"))
            .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(textNdjson("All done."))));
    }

    private static String toolCallNdjson(String toolName, String argumentsJson) {
        String ts = "2026-04-25T00:00:00Z";
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts + "\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"" + toolName + "\",\"arguments\":"
            + argumentsJson + "}}]},\"done\":false}\n"
            + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
            + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
            + "\"prompt_eval_count\":5,\"eval_count\":3}\n";
    }

    private static String textNdjson(String text) {
        String ts = "2026-04-25T00:00:01Z";
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts + "\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"" + text + "\"},\"done\":false}\n"
            + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
            + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
            + "\"prompt_eval_count\":9,\"eval_count\":4}\n";
    }

    private static String embedJson() {
        return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}";
    }

    private static String openAiEmbeddingJson() {
        return "{\"data\":[{\"embedding\":" + vector(1024)
            + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }

    private static String vector(int size) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                embedding.append(",");
            }
            embedding.append("0.01");
        }
        return embedding.append("]").toString();
    }
}
