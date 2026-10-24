package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** With a Grafana URL the overview links the dashboard and Explore on each backend, in a new tab. */
@QuarkusTest
@TestProfile(ObservabilityLinksConfiguredTest.WithGrafana.class)
class ObservabilityLinksConfiguredTest {

  public static class WithGrafana implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qlawkus.console.grafana-url", "http://grafana.example:3000/");
    }
  }

  @Test
  void overview_linksTheDashboardAndExploreOnEachBackend() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("Observability"))
        .body(containsString("href=\"http://grafana.example:3000/d/qlawkus-agent\""))
        .body(containsString("href=\"http://grafana.example:3000/explore?schemaVersion=1&amp;panes="))
        .body(containsString("%22uid%22%3A%22prometheus%22"))
        .body(containsString("%22uid%22%3A%22tempo%22"))
        .body(containsString("%22uid%22%3A%22loki%22"))
        .body(containsString("target=\"_blank\" rel=\"noopener noreferrer\""));
  }

  @Test
  void configPage_listsTheNewProperty() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console/config")
        .then().statusCode(200)
        .body(containsString("qlawkus.console.grafana-url"));
  }
}
