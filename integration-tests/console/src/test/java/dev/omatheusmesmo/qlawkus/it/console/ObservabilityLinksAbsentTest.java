package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** With no Grafana URL configured the overview carries no observability links at all. */
@QuarkusTest
class ObservabilityLinksAbsentTest {

  @Test
  void overview_hasNoObservabilityCardWhenUnconfigured() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console")
        .then().statusCode(200)
        .body(not(containsString("Observability")))
        .body(not(containsString("/explore")));
  }
}
