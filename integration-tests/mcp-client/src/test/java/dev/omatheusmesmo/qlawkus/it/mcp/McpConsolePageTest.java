package dev.omatheusmesmo.qlawkus.it.mcp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Exercises the optional console and MCP extensions together, without a live model or MCP server. */
@QuarkusTest
class McpConsolePageTest {
    @Test
    void pageRequiresAuthenticationAndRendersTheInstalledCapability() {
        given().get("/console/mcp").then().statusCode(401);
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console/mcp")
                .then().statusCode(200).body(containsString("MCP connections"))
                .body(containsString("/console/mcp.js"), containsString("mcp-secret-value"),
                        containsString("type=\"password\""), containsString("Browser sign-in (SSO)"));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console")
                .then().statusCode(200).body(containsString("href=\"/console/mcp\""));
    }

    @Test
    void sameAdminRegistryUsedByUiPersistsDisabledConnections() {
        var auth = given().auth().preemptive().basic("qlawkus", "qlawkus-test");
        auth.contentType("application/json").body("""
                {"id":"console-fixture","transport":"HTTP","enabled":false,
                 "url":"http://127.0.0.1:1/mcp","command":[],"headerSecretRefs":{},"environmentSecretRefs":{}}
                """).put("/api/admin/mcp/connections/console-fixture").then().statusCode(200).body("state", equalTo("DISABLED"));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/api/admin/mcp/connections")
                .then().statusCode(200).body("id", hasItem("console-fixture"));
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").delete("/api/admin/mcp/connections/console-fixture")
                .then().statusCode(204);
    }

    @Test
    void browserAssetUsesSameOriginRequestsAndTextRendering() {
        given().get("/console/mcp.js").then().statusCode(200)
                .body(containsString("credentials: \"same-origin\""), containsString("textContent"),
                        not(containsString("innerHTML")), not(containsString("localStorage")));
    }
}
