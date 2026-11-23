package dev.omatheusmesmo.qlawkus.it.console;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
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
                .statusCode(200).body("settings.enabled", equalTo(false));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body(CONFIGURE_OFF).put(SETTINGS).then()
                .statusCode(200).body("settings.intervalMinutes", equalTo(3))
                .body("settings.prompt", equalTo("Review schedule health."));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(SETTINGS).then()
                .statusCode(200).header("Cache-Control", containsString("no-store"))
                .body("settings.prompt", equalTo("Review schedule health."));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console/schedule").then()
                .statusCode(200).body(containsString("id=\"heartbeat\""))
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
}
