package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins that a messaging turn, which runs through the declarative {@link QlawkusAgent}, may chain
 * more than the upstream default of ten sequential tool calls. Before the fix the eleventh tool round
 * raised "exceeded 10 sequential tool executions", and the orchestrator replayed the whole turn on the
 * fallback path. The model-call count pins that no replay happens: one call per tool round plus the
 * final answer.
 */
@QuarkusTest
@ConnectWireMock
class MessagingTurnToolLimitTest {

    private static final String TS = "2026-04-25T00:00:00Z";
    private static final int TOOL_ROUNDS = 12;
    private static final String FINAL_REPLY = "all twelve done";

    WireMock wiremock;

    @Inject
    AgentRunner runner;

    @Test
    void aMessagingTurnRunsMoreThanTenSequentialToolCallsWithoutReplay() {
        String token = "tool-limit-" + UUID.randomUUID();
        String conversation = "tool-limit-conversation-" + UUID.randomUUID();
        stubToolRounds(token);

        String reply = runner.chat(conversation, "run " + token);

        assertEquals(FINAL_REPLY, reply);
        wiremock.verify(TOOL_ROUNDS + 1, WireMock.postRequestedFor(WireMock.urlEqualTo("/api/chat"))
                .withRequestBody(WireMock.containing(token)));
    }

    private void stubToolRounds(String token) {
        String scenario = "tool-rounds-" + token;
        wiremock.resetMappings();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed")).willReturn(WireMock.okJson(
                "{\"model\":\"x\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}")));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings")).willReturn(WireMock.okJson(
                "{\"data\":[{\"embedding\":" + vector(1024) + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
        for (int round = 0; round < TOOL_ROUNDS; round++) {
            wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(stateBefore(round))
                    .withRequestBody(WireMock.containing(token))
                    .willSetStateTo(stateBefore(round + 1))
                    .willReturn(WireMock.okJson(toolCall(round))));
        }
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .inScenario(scenario)
                .whenScenarioStateIs(stateBefore(TOOL_ROUNDS))
                .withRequestBody(WireMock.containing(token))
                .willReturn(WireMock.okJson(textReply(FINAL_REPLY))));
    }

    private static String stateBefore(int round) {
        return round == 0 ? Scenario.STARTED : "after-round-" + round;
    }

    private static String toolCall(int round) {
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"searchMemories\","
                + "\"arguments\":{\"query\":\"round " + round + "\"}}}]},"
                + "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":5,\"eval_count\":3}";
    }

    private static String textReply(String text) {
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + text + "\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":9,\"eval_count\":4}";
    }

    private static String vector(int size) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            embedding.append(i > 0 ? "," : "").append("0.01");
        }
        return embedding.append("]").toString();
    }
}
