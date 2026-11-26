package dev.omatheusmesmo.qlawkus.ops;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.tool.quarantine.ToolQuarantine;
import jakarta.inject.Inject;

@QuarkusTest
class OpsDashboardTest {

    @Inject
    ToolQuarantine quarantine;

    @Test
    void adminDashboardIsPlainHtmlWithTools() {
        RestAssured.given()
                .auth().preemptive().basic("qlawkus", "qlawkus-test")
                .when().get("/api/admin/ops/dashboard")
                .then()
                .statusCode(200)
                .body(containsString("Tools ("))
                .body(containsString("/q/metrics"));
    }

    @Test
    void aQuarantinedToolIsListedOnlyWhileItIsOut() {
        RestAssured.given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .when().get("/api/admin/ops/dashboard").then().body(not(containsString("Quarantined tools")));
        try {
            for (int i = 0; i < 5; i++) {
                quarantine.recordFailure("dashboardProbe");
            }

            RestAssured.given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                    .when().get("/api/admin/ops/dashboard").then().statusCode(200)
                    .body(containsString("Quarantined tools (1)")).body(containsString("dashboardProbe"));
        } finally {
            quarantine.release("dashboardProbe");
        }
    }
}
