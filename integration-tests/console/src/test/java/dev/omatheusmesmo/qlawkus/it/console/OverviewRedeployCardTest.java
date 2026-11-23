package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OverviewRedeployCardTest {

    private static final String USER = "qlawkus";
    private static final String PASS = "qlawkus-test";

    @Test
    void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
        String page = get("/console");

        assertThat(page, containsString("hx-get=\"/console/overview/redeploy\""));
        assertThat(page, containsString("every 60s [document.visibilityState=='visible']"));
    }

    @Test
    void theCardAsksForAuthentication() {
        given().get("/console/overview/redeploy").then().statusCode(401);
    }

    @Test
    void aFailedRunNamesThePhaseThatFailedAndEscapesTheMessage() {
        report("""
            {"startedAt":"2026-10-01T12:00:00Z","outcome":"failure","failedPhase":"restart",
             "phases":{"fetch":120,"build":61500,"restart":2300},
             "message":"rollout <b>timed out</b>"}
            """);

        String card = get("/console/overview/redeploy");

        assertThat(card, containsString("data-outcome=\"failure\""));
        assertThat(card, containsString("failed in restart"));
        assertThat(card, containsString("rollout &lt;b&gt;timed out&lt;/b&gt;"));
        assertThat(card, not(containsString("<b>timed out</b>")));
    }

    @Test
    void aSuccessfulRunSaysSoWithoutAFailedPhase() {
        report("""
            {"startedAt":"2026-10-02T12:00:00Z","outcome":"success",
             "phases":{"fetch":100,"build":42000,"restart":1500}}
            """);

        String card = get("/console/overview/redeploy");

        assertThat(card, containsString("data-outcome=\"success\""));
        assertThat(card, not(containsString("failed in")));
        assertThat(card, containsString("Last success"));
    }

    @Test
    void theCardReportsTheTotalTimeOfTheRun() {
        report("""
            {"startedAt":"2026-10-03T12:00:00Z","outcome":"success",
             "phases":{"fetch":1000,"build":60000,"restart":2000}}
            """);

        assertThat(get("/console/overview/redeploy"), containsString("63.0 s"));
    }

    private static void report(String body) {
        given().auth().preemptive().basic(USER, PASS).contentType("application/json").body(body)
                .post("/api/admin/composition/redeploys").then().statusCode(201);
    }

    private static String get(String path) {
        return given().auth().preemptive().basic(USER, PASS).get(path)
                .then().statusCode(200).extract().asString();
    }
}
