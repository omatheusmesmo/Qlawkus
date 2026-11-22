package dev.omatheusmesmo.qlawkus.audit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import io.agroal.api.AgroalDataSource;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(DebugHistoryTest.Enabled.class)
class DebugHistoryTest {
    private static final String RESOURCE = "/api/admin/debug-history";
    private static final String FAKE_KEY = "sk-abcdef0123456789abcdef0123456789";

    public static class Enabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.debug-history.enabled", "true",
                    "qlawkus.debug-history.capture-text", "true",
                    "qlawkus.reply-language.enabled", "false",
                    "qlawkus.agent.active-memory.enabled", "false",
                    "qlawkus.agent.transcript-archive.enabled", "false",
                    "qlawkus.agent.semantic-extractor.enabled", "false");
        }
    }

    @Inject DynamicTaskScheduler scheduler;
    @Inject PgDebugJournal journal;
    @Inject AgroalDataSource dataSource;

    WireMock wiremock;

    @Test
    void aScheduledTurnPersistsModelRequestsResponsesAndToolCallsTogether() {
        var token = UUID.randomUUID().toString();
        stubTurn(token);
        var task = scheduler.create("audit-" + token, new TaskDestination("discord", "test-chat"),
                "Check skill " + token, scheduler.cronInMinutes(20)).task();
        scheduler.fire(task.id());

        var byTask = new PgDebugJournal.Filter(task.id(), null, null, null);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var taskEvents = journal.recent(byTask, 100, false);
            assertEquals(List.of("QUEUED", "STARTED", "SCHEDULED"),
                    taskEvents.stream().map(DebugEntry::outcome).toList());
        });
        UUID run = journal.recent(byTask, 100, false).getFirst().runId();

        given().when().get(RESOURCE).then().statusCode(401);
        var visible = given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .queryParam("runId", run.toString()).get(RESOURCE).then().statusCode(200)
                .header("Cache-Control", equalTo("no-store")).extract().asString();
        assertFalse(visible.contains(FAKE_KEY));
        assertFalse(visible.contains("[redacted]"));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .queryParam("runId", run.toString()).queryParam("includeText", true)
                .get(RESOURCE).then().statusCode(200)
                .body("events.find { it.excerpt != null }.excerpt", equalTo("Done [redacted]"));

        var events = journal.recent(new PgDebugJournal.Filter(null, run, null, null), 100, true);
        assertTrue(events.stream().anyMatch(event -> event.kind().equals("model.request")));
        assertEquals(2, events.stream().filter(event -> event.kind().equals("model.response")).count());
        var attempts = events.stream().filter(event -> event.kind().equals("model.attempt"))
                .collect(Collectors.groupingBy(DebugEntry::callId));
        assertEquals(2, attempts.size());
        for (var attempt : attempts.values()) {
            assertEquals(List.of("COMPLETED", "STARTED"), attempt.stream().map(DebugEntry::outcome).toList());
            assertTrue(attempt.getFirst().detail().path("durationMs").isNumber());
            assertTrue(attempt.getLast().detail().path("messages").asInt() > 0);
        }
        assertTrue(events.stream().anyMatch(event -> event.kind().equals("turn")
                && event.outcome().equals("STARTED") && event.excerpt() == null));
        assertTrue(events.stream().anyMatch(event -> event.kind().equals("model.response")
                && "Done [redacted]".equals(event.excerpt()) && event.detail().path("outputTokens").asInt() == 4));

        var tools = events.stream().filter(event -> event.kind().equals("tool")).toList();
        assertEquals(List.of("EXECUTED", "REQUESTED"), tools.stream().map(DebugEntry::outcome).toList());
        assertEquals("cancelScheduledTask", tools.getFirst().subject());
        assertEquals(tools.getFirst().callId(), tools.getLast().callId());
        assertEquals(tools.getFirst().invocationId(), tools.getLast().invocationId());
        assertFalse(tools.toString().contains("private-tool-argument"));
        assertFalse(events.toString().contains(FAKE_KEY));

        var byInvocation = new PgDebugJournal.Filter(null, run, tools.getFirst().invocationId(), null);
        assertEquals(Set.of("turn", "model.request", "model.response", "tool"),
                journal.recent(byInvocation, 100, true).stream().map(DebugEntry::kind).collect(Collectors.toSet()));
    }

    @Test
    void excerptsExpireBeforeMetadata() throws SQLException {
        var at = OffsetDateTime.now(ZoneOffset.UTC).minus(2, ChronoUnit.DAYS);
        var run = UUID.randomUUID();
        try (var db = dataSource.getConnection();
             var insert = db.prepareStatement("""
                     INSERT INTO debug_event (occurred_at, kind, run_id, detail, excerpt)
                     VALUES (?, 'turn', ?, '{}'::jsonb, 'expired excerpt')
                     """)) {
            insert.setObject(1, at);
            insert.setObject(2, run);
            insert.executeUpdate();
        }
        var events = journal.recent(new PgDebugJournal.Filter(null, run, null, null), 100, true);
        assertEquals(1, events.size());
        assertNull(events.getFirst().excerpt());
    }

    private void stubTurn(String token) {
        String tool = """
                {"model":"gemma4:e2b","created_at":"2026-04-25T00:00:00Z",
                 "message":{"role":"assistant","content":"","tool_calls":[
                   {"function":{"name":"cancelScheduledTask","arguments":{"taskId":"private-tool-argument"}}}]},
                 "done":true,"done_reason":"stop","prompt_eval_count":5,"eval_count":3}
                """;
        String reply = """
                {"model":"gemma4:e2b","created_at":"2026-04-25T00:00:00Z",
                 "message":{"role":"assistant","content":"Done %s"},
                 "done":true,"done_reason":"stop","prompt_eval_count":9,"eval_count":4}
                """.formatted(FAKE_KEY);
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(2)
                .withRequestBody(WireMock.containing(token)).willReturn(WireMock.okJson(tool)));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(1)
                .withRequestBody(WireMock.matching("(?s).*" + token + ".*\"role\"\\s*:\\s*\"tool\".*"))
                .willReturn(WireMock.okJson(reply)));
    }
}
