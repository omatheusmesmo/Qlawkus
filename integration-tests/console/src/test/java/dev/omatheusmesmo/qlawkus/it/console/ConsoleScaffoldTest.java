package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the console scaffold over HTTP: the page is gated by the shared {@code @Authenticated}
 * mechanism, the Qute frame renders, the HTMX status fragment reports live composition state, and
 * the vendored static assets are served. Proves the whole Qute + HTMX + auth stack is wired without
 * a database (this module has no pgvector).
 */
@QuarkusTest
class ConsoleScaffoldTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Test
  void landingPage_requiresAuthentication() {
    given().when().get("/console").then().statusCode(401);
    given().when().get("/console/status").then().statusCode(401);
  }

  @Test
  void landingPage_rendersTheFrame() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("Qlawkus"))
        .body(containsString("class=\"sidebar\""))
        .body(containsString("/console/htmx.min.js"))
        .body(containsString("hx-get=\"/console/status\""));
  }

  @Test
  void statusFragment_reportsLiveCompositionState() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/status")
        .then().statusCode(200)
        .body(containsString("up"))
        .body(containsString("default: disabled"))
        .body(containsString("console"));
  }

  @Test
  void staticAssets_areServed() {
    given().when().get("/console/app.css").then().statusCode(200);
    given().when().get("/console/htmx.min.js").then().statusCode(200);
  }
}
