package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The capability-omission proof for the install panel: this distribution carries the console without the
 * console-skill-hub extension, so the install page is not served and the skills page has no link to it.
 */
@QuarkusTest
class ConsoleSkillHubAbsentTest {

    private static final String USER = "qlawkus";
    private static final String PASS = "qlawkus-test";

    @Test
    void theInstallPageIsNotServedWithoutTheExtension() {
        given().auth().preemptive().basic(USER, PASS)
                .when().get("/console/skills/install")
                .then().statusCode(404);
    }

    @Test
    void theSkillsPageHasNoInstallLinkWithoutTheExtension() {
        given().auth().preemptive().basic(USER, PASS)
                .when().get("/console/skills")
                .then().statusCode(200)
                .body(not(containsString("href=\"/console/skills/install\"")))
                .body(containsString("href=\"/console/memory\""));
    }
}
