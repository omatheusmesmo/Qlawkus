package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The first-run banner on a fresh install. The rest of the module points the primary model at
 * WireMock on loopback, which the wizard reads as a self-hosted endpoint; this test pins a cloud
 * base-url so a placeholder key really does mean "no LLM yet".
 */
@QuarkusTest
@TestProfile(SetupBannerTest.CloudEndpoint.class)
class SetupBannerTest {

  public static class CloudEndpoint implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.langchain4j.openai.\"primary\".base-url", "https://api.openai.com/v1");
    }
  }

  @Test
  void console_showsSetupBannerWhenUnconfigured() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("setup-banner"))
        .body(containsString("/console/setup"));
  }
}
