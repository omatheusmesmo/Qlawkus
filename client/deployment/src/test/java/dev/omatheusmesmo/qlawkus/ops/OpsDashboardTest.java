package dev.omatheusmesmo.qlawkus.ops;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class OpsDashboardTest {

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
}
