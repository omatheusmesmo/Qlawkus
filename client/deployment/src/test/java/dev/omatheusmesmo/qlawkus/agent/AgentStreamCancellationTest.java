package dev.omatheusmesmo.qlawkus.agent;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that cancelling the {@link AgentStream} Multi stops the model and tool loop when the cancel lands
 * while a tool is in flight: the tool is held open until after the cancel, and no model round or tool
 * starts afterwards. A cancel that lands after the framework has already issued the next round is not
 * covered here, because that ordering is not deterministic.
 */
@QuarkusTest
@ConnectWireMock
class AgentStreamCancellationTest {

    private static final String SCENARIO = "two-tools";
    private static final String AFTER_FIRST_TOOL = "after-first-tool";
    private static final String AFTER_SECOND_TOOL = "after-second-tool";

    @Inject
    AgentStream stream;

    WireMock wiremock;

    @BeforeEach
    void stubs() {
        wiremock.resetMappings();
        CancellationProbeTool.EVENTS.clear();
        wiremock.register(post(urlEqualTo("/api/embed"))
            .willReturn(WireMock.okJson(embedJson())));
        wiremock.register(post(urlEqualTo("/v1/embeddings"))
            .willReturn(WireMock.okJson(openAiEmbeddingJson())));
        wiremock.register(post(urlEqualTo("/api/chat")).inScenario(SCENARIO).whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(toolCallNdjson("probeStep", "{\"step\":\"first\"}")))
            .willSetStateTo(AFTER_FIRST_TOOL));
        wiremock.register(post(urlEqualTo("/api/chat")).inScenario(SCENARIO).whenScenarioStateIs(AFTER_FIRST_TOOL)
            .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(toolCallNdjson("probeStep", "{\"step\":\"second\"}")))
            .willSetStateTo(AFTER_SECOND_TOOL));
        wiremock.register(post(urlEqualTo("/api/chat")).inScenario(SCENARIO).whenScenarioStateIs(AFTER_SECOND_TOOL)
            .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                .withBody(textNdjson("finished"))));
    }

    @AfterEach
    void releaseGate() {
        CountDownLatch held = CancellationProbeTool.gate;
        CancellationProbeTool.gate = null;
        if (held != null) {
            held.countDown();
        }
    }

    @Test
    void cancellingWhileTheFirstToolIsInFlight() throws InterruptedException {
        CancellationProbeTool.heldStep = "first";
        runCancelProbe("start:first", "first");
    }

    @Test
    void cancellingWhileTheSecondToolIsInFlight() throws InterruptedException {
        CancellationProbeTool.heldStep = "second";
        runCancelProbe("start:second", "second");
    }

    private void runCancelProbe(String heldEvent, String label) throws InterruptedException {
        AssertSubscriber<AgentEvent> subscriber = startTurn();
        CancellationProbeTool.gate = new CountDownLatch(1);
        awaitEvent(heldEvent);
        int chatBeforeCancel = chatCalls();
        int eventsBeforeCancel = CancellationProbeTool.EVENTS.size();
        subscriber.cancel();
        CancellationProbeTool.gate.countDown();
        Thread.sleep(4_000);

        List<String> eventsAfterCancel = List.copyOf(CancellationProbeTool.EVENTS);
        assertEquals(0, chatCalls() - chatBeforeCancel, label + ": no model round starts after the cancel");
        assertFalse(eventsAfterCancel.stream().skip(eventsBeforeCancel).anyMatch(e -> e.startsWith("start:")),
            label + ": no tool starts after the cancel: " + eventsAfterCancel);
    }

    private AssertSubscriber<AgentEvent> startTurn() {
        return stream.stream("cancel-" + UUID.randomUUID(), "run the two step probe")
            .subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
    }

    private void awaitEvent(String event) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!CancellationProbeTool.EVENTS.contains(event) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    private int chatCalls() {
        return wiremock.find(postRequestedFor(urlEqualTo("/api/chat"))).size();
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
