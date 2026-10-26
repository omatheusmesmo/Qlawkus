package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The chat page hands the configured {@code qlawkus.console.chat.tool-progress} mode to its script,
 * which sends it with every message so the endpoint omits what the mode hides.
 */
@QuarkusTest
@TestProfile(ChatToolProgressConfigTest.VerboseProfile.class)
class ChatToolProgressConfigTest {

  public static class VerboseProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qlawkus.console.chat.tool-progress", "verbose");
    }
  }

  @Test
  void page_carriesTheConfiguredMode() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console/chat")
        .then().statusCode(200)
        .body(containsString("data-tool-progress=\"verbose\""));
  }
}
