package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The capability-omission proof: this distribution carries the console without the voice extension, so the
 * voice page is not served and the navigation has no Voice entry to link to it.
 */
@QuarkusTest
class ConsoleVoiceAbsentTest {

    private static final String USER = "qlawkus";
    private static final String PASS = "qlawkus-test";

    @Test
    void theVoicePageIsNotServedWithoutTheExtension() {
        given().auth().preemptive().basic(USER, PASS)
                .when().get("/console/voice")
                .then().statusCode(404);
    }

    @Test
    void theNavigationHasNoVoiceEntryWithoutTheExtension() {
        given().auth().preemptive().basic(USER, PASS)
                .when().get("/console")
                .then().statusCode(200)
                .body(not(containsString("href=\"/console/voice\"")))
                .body(containsString("href=\"/console/chat\""));
    }
}
