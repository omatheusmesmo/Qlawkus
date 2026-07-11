package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the management pages (memory, skills, cognition) over HTTP, database-free. Proves the
 * hybrid design: the pages render rich read views from the client-side stores and wire the mutating
 * actions to the existing admin endpoints, and the cognition page is gated by the baked manifest
 * (this markdown-only module does not compose cognition.pgvector, so it shows "not available").
 */
@QuarkusTest
class ManagementConsoleTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Test
  void pages_requireAuthentication() {
    given().when().get("/console/memory").then().statusCode(401);
    given().when().get("/console/skills").then().statusCode(401);
    given().when().get("/console/cognition").then().statusCode(401);
  }

  @Test
  void memoryPage_rendersReadViewAndActions() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/memory")
        .then().statusCode(200)
        .body(containsString("<h1>Memory</h1>"))
        .body(containsString("hx-post=\"/api/admin/memory/review\""))
        .body(containsString("hx-post=\"/api/admin/memory/curate\""))
        .body(containsString("hx-delete=\"/api/admin/memory?all=true\""));
  }

  @Test
  void skillsPage_rendersTableAndActions() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/skills")
        .then().statusCode(200)
        .body(containsString("<h1>Skills</h1>"))
        .body(containsString("hx-post=\"/api/admin/skills/curate\""))
        .body(containsString("hx-post=\"/api/admin/skills/lifecycle\""));
  }

  @Test
  void cognitionPage_showsUnavailableInMarkdownBuild() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/cognition")
        .then().statusCode(200)
        .body(containsString("<h1>Cognition</h1>"))
        .body(containsString("Not available"));
  }

  @Test
  void sharedNav_linksTheManagementPages() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/memory")
        .then().statusCode(200)
        .body(containsString("href=\"/console/skills\""))
        .body(containsString("href=\"/console/cognition\""));
  }
}
