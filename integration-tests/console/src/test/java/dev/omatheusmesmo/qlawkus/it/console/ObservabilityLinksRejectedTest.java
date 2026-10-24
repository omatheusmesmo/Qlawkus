package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A non-http(s) Grafana URL never reaches an href: the card is left out and the agent still boots. */
@QuarkusTest
@TestProfile(ObservabilityLinksRejectedTest.WithScriptUrl.class)
class ObservabilityLinksRejectedTest {

  public static class WithScriptUrl implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qlawkus.console.grafana-url", "javascript:alert(1)");
    }
  }

  @Test
  void overview_rendersNoLinkForAScriptUrl() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console")
        .then().statusCode(200)
        .body(not(containsString("Observability")))
        .body(not(containsString("javascript:")));
  }
}
