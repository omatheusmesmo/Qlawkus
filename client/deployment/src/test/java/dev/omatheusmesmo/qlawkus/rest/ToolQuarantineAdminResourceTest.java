package dev.omatheusmesmo.qlawkus.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.tool.quarantine.ToolQuarantine;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ToolQuarantineAdminResourceTest {

    @Inject
    ToolQuarantine quarantine;

    @AfterEach
    void clean() {
        quarantine.release("brokenTool");
    }

    @Test
    void everyEndpointRefusesAnonymousCallers() {
        given().when().get("/api/admin/tool-quarantine").then().statusCode(401);
        given().when().delete("/api/admin/tool-quarantine/brokenTool").then().statusCode(401);
    }

    @Test
    void theOwnerSeesAQuarantinedToolAndCanReleaseIt() {
        for (int i = 0; i < 5; i++) {
            quarantine.recordFailure("brokenTool");
        }

        given().auth().basic("qlawkus", "qlawkus-test").when().get("/api/admin/tool-quarantine").then()
                .statusCode(200).body("tool", hasItem("brokenTool"));

        given().auth().basic("qlawkus", "qlawkus-test").when().delete("/api/admin/tool-quarantine/brokenTool")
                .then().statusCode(204);

        given().auth().basic("qlawkus", "qlawkus-test").when().get("/api/admin/tool-quarantine").then()
                .statusCode(200).body("tool", not(hasItem("brokenTool")));
    }

    @Test
    void releasingAToolThatIsNotQuarantinedIsNotFound() {
        given().auth().basic("qlawkus", "qlawkus-test").when().delete("/api/admin/tool-quarantine/brokenTool")
                .then().statusCode(404);
        given().auth().basic("qlawkus", "qlawkus-test").when().get("/api/admin/tool-quarantine").then()
                .statusCode(200).body("findAll { it.tool == 'brokenTool' }", hasSize(0));
    }
}
