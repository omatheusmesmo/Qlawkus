package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class McpConsoleAbsentTest {
    @Test
    void consoleWithoutMcpDoesNotAdvertiseOrServeItsPage() {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console")
                .then().statusCode(200).body(not(containsString("href=\"/console/mcp\"")));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console/mcp")
                .then().statusCode(404);
    }
}
