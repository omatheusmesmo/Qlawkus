package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * This distribution has the console and the skill hub and nothing else optional: no health checks, no
 * pgvector, no MCP client, no specialists. The overview must still render, every card must answer,
 * and a card for a capability that is not there must not be rendered at all.
 */
@QuarkusTest
class OverviewMinimalDistributionTest {

    @Test
    void theOverviewRendersWithoutTheHealthCard() {
        String page = get("/console");

        assertThat(page, containsString("card-model"));
        assertThat(page, containsString("card-memory"));
        assertThat(page, not(containsString("card-health")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"upcoming", "redeploy", "memory", "model"})
    void everyCardThatDoesNotNeedAnOptionalCapabilityAnswers(String card) {
        assertThat(get("/console/overview/" + card), not(containsString("data-card-error")));
    }

    @Test
    void theHealthEndpointSaysHealthIsNotEnabledInsteadOfFailing() {
        assertThat(get("/console/overview/health"), containsString("not enabled in this distribution"));
    }

    private static String get(String path) {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
                .then().statusCode(200).extract().asString();
    }
}
