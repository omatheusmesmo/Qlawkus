package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The voice page sits behind the console's credentials, and the console's navigation shows the Voice entry
 * because this distribution ships the extension.
 */
@QuarkusTest
class VoicePageTest {

    @Test
    void theVoicePageRequiresCredentials() {
        given().when().get("/console/voice").then().statusCode(401);
    }

    @Test
    void theVoicePageRendersTheMicrophoneControlsForTheOwner() {
        given().auth().basic(VoiceTestSupport.USER, VoiceTestSupport.PASSWORD)
                .when().get("/console/voice")
                .then().statusCode(200)
                .body(containsString("id=\"voice-toggle\""))
                .body(containsString("data-socket=\"/console/voice/ws\""))
                .body(containsString("/console/voice.js"));
    }

    @Test
    void theConsoleNavigationLinksTheVoicePage() {
        given().auth().basic(VoiceTestSupport.USER, VoiceTestSupport.PASSWORD)
                .when().get("/console")
                .then().statusCode(200)
                .body(containsString("href=\"/console/voice\""))
                .body(not(containsString("href=\"/console/voice\" hidden")));
    }
}
