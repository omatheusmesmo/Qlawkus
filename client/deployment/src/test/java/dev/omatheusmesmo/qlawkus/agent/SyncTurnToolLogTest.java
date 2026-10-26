package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.logging.CapturedLogs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pins that a synchronous turn, the path the messaging channels take, lists every tool it ran in its
 * log line, including the tools declared on the AI service itself, which never pass through the
 * {@code QlawToolProvider} decorators.
 */
@QuarkusTest
@ConnectWireMock
class SyncTurnToolLogTest {

    private static final String TS = "2026-04-25T00:00:00Z";

    WireMock wiremock;

    @Inject
    AgentService agent;

    @Test
    void aSyncTurnListsTheServiceDeclaredToolItRan() {
        String token = "sync-turn-" + UUID.randomUUID();
        String conversation = "sync-log-" + UUID.randomUUID();
        stubOneToolTurn(token);

        try (CapturedLogs logs = new CapturedLogs()) {
            agent.chatSync(conversation, "load the skill " + token);

            List<String> turns = logs.info().stream().map(r -> r.getMessage())
                    .filter(m -> m.startsWith("Agent turn") && m.contains("conversation=" + conversation))
                    .toList();
            assertEquals(1, turns.size(), "one line for the turn: " + turns);
            assertTrue(turns.get(0).contains("tools=1 [viewSkill]"), turns.get(0));
        }
    }

    private void stubOneToolTurn(String token) {
        String toolCall = "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"viewSkill\",\"arguments\":{\"name\":\"none\"}}}]},"
                + "\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":5,\"eval_count\":3}";
        String text = "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + TS + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"done\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":9,\"eval_count\":4}";
        wiremock.resetMappings();
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed")).willReturn(WireMock.okJson(
                "{\"model\":\"x\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}")));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings")).willReturn(WireMock.okJson(
                "{\"data\":[{\"embedding\":" + vector(1024) + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(10).willReturn(WireMock.okJson(text)));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(2)
                .withRequestBody(WireMock.containing(token)).willReturn(WireMock.okJson(toolCall)));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(1)
                .withRequestBody(WireMock.matching("(?s).*" + Pattern.quote(token) + ".*\"role\"\\s*:\\s*\"tool\".*"))
                .willReturn(WireMock.okJson(text)));
    }

    private static String vector(int size) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            embedding.append(i > 0 ? "," : "").append("0.01");
        }
        return embedding.append("]").toString();
    }
}
