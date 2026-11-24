package dev.omatheusmesmo.qlawkus.it.mcp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The console's Health card lists the MCP client's readiness next to the core checks. */
@QuarkusTest
class McpHealthCardTest {

    private static final String SIGN_IN_CONNECTION = """
            {"id":"sso","transport":"HTTP","enabled":true,"url":"https://93.184.216.34/mcp",
             "command":[],"headerSecretRefs":{},"environmentSecretRefs":{},
             "oauth":{"grant":"AUTHORIZATION_CODE","tokenEndpoint":"https://93.184.216.34/token",
                      "authorizationEndpoint":"https://93.184.216.34/authorize","clientId":"client",
                      "redirectUri":"https://agent.example/api/admin/mcp/oauth/callback","scopes":[]}}
            """;

    @Test
    void theCardListsTheMcpClientCheckAsUpWithNoConnections() {
        String card = card();

        assertThat(card, containsString("data-check=\"qlawkus-mcp-client\" data-status=\"UP\""));
        assertThat(card, containsString("data-check=\"qlawkus-model\""));
    }

    @Test
    void aConnectionWaitingForSignInShowsTheRowDegradedWithItsIdAndNothingElse() {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json")
                .body(SIGN_IN_CONNECTION).put("/api/admin/mcp/connections/sso").then().statusCode(200);
        try {
            String card = card();

            assertThat(card, containsString("data-check=\"qlawkus-mcp-client\" data-status=\"DEGRADED\""));
            assertThat(card, containsString("sso needs sign-in"));
            assertThat(card, not(containsString("93.184.216.34")));
        } finally {
            given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                    .delete("/api/admin/mcp/connections/sso").then().statusCode(204);
        }
    }

    private static String card() {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get("/console/overview/health")
                .then().statusCode(200).extract().asString();
    }
}
