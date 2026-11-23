package dev.omatheusmesmo.qlawkus.it.console;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(value = HeartbeatConsoleTest.StateRoot.class, restrictToAnnotatedClass = true)
class HeartbeatConsoleTest {

    private static final String SETTINGS = "/api/admin/heartbeat";
    private static final String CONFIGURE_OFF = """
            {"enabled":false,"intervalMinutes":3,"prompt":"Review schedule health.","provider":null}
            """;

    @Test
    void exposesSettingsOnlyToTheOwnerAndRendersTheSchedulePanel() {
        given().get(SETTINGS).then().statusCode(401);
        given().contentType("application/json").body(CONFIGURE_OFF).put(SETTINGS).then().statusCode(401);
        given().auth().preemptive().basic("other", "other-test").get(SETTINGS).then().statusCode(403);
        given().auth().preemptive().basic("other", "other-test").contentType("application/json")
                .body(CONFIGURE_OFF).put(SETTINGS).then().statusCode(403);

        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(SETTINGS).then()
                .statusCode(200).body("settings.enabled", equalTo(true))
                .body("settings.intervalMinutes", equalTo(30))
                .body("settings.prompt", containsString("missed or invalid scheduled tasks"))
                .body("settings.provider", nullValue())
                .body("nextRun", notNullValue());
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body(CONFIGURE_OFF).put(SETTINGS).then()
                .statusCode(200).body("settings.intervalMinutes", equalTo(3))
                .body("settings.prompt", equalTo("Review schedule health."));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(SETTINGS).then()
                .statusCode(200).header("Cache-Control", containsString("no-store"))
                .body("settings.prompt", equalTo("Review schedule health."));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body("""
                        {"startedAt":"2026-01-01T00:00:00Z","outcome":"success",
                         "phases":{"verify":123},"message":"Fixture verified the redeploy."}
                        """)
                .post("/api/admin/composition/redeploys").then().statusCode(201);
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console/schedule").then()
                .statusCode(200).body(containsString("id=\"heartbeat\""))
                .body(containsString("Memory review (dedup facts)"))
                .body(containsString("Fixture verified the redeploy."))
                .body(containsString("123 ms"))
                .body(not(containsString("Review schedule health.")));
    }

    @Test
    void rejectsInvalidSettingsAndRecognizedCredentialsWithoutEchoingThem() {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body(CONFIGURE_OFF.replace("Minutes\":3", "Minutes\":0"))
                .put(SETTINGS).then().statusCode(400).body("error", containsString("interval"));

        String example = "sk-" + "q".repeat(24);
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body(CONFIGURE_OFF.replace("Review schedule health.", example))
                .put(SETTINGS).then().statusCode(400)
                .body("error", containsString("Remove credentials"))
                .body(not(containsString(example)));
    }

    public static class StateRoot implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            Path state = Path.of("target", "heartbeat-console", UUID.randomUUID().toString()).toAbsolutePath();
            return Map.of("qlawkus.agent.state.root", state.toString());
        }

        @Override
        public void stop() {
        }
    }
}
