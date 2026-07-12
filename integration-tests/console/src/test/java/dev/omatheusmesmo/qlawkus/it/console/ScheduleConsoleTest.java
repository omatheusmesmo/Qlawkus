package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the schedule page over HTTP: it lists the five background jobs sourced from the live
 * {@code Scheduler} and wires cron edits to the same runtime-toggles endpoint the config editor
 * uses, plus each job's existing manual-trigger endpoint.
 */
@QuarkusTest
class ScheduleConsoleTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Test
  void page_requiresAuthentication() {
    given().when().get("/console/schedule").then().statusCode(401);
  }

  @Test
  void page_listsAllFiveJobsWithCronAndTriggers() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/schedule")
        .then().statusCode(200)
        .body(containsString("<h1>Schedule</h1>"))
        .body(containsString("qlawkus.memory-review.cron"))
        .body(containsString("qlawkus.memory-curation.cron"))
        .body(containsString("qlawkus.consolidator.cron"))
        .body(containsString("qlawkus.skills.curation.cron"))
        .body(containsString("qlawkus.skills.lifecycle.cron"))
        .body(containsString("hx-post=\"/api/admin/memory/review\""))
        .body(containsString("hx-post=\"/api/admin/memory/curate\""))
        .body(containsString("hx-post=\"/api/admin/memory/consolidate\""))
        .body(containsString("hx-post=\"/api/admin/skills/curate\""))
        .body(containsString("hx-post=\"/api/admin/skills/lifecycle\""))
        .body(containsString("hx-put=\"/api/admin/runtime-toggles\""));
  }

  @Test
  void sharedNav_linksTheSchedulePage() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/config")
        .then().statusCode(200)
        .body(containsString("href=\"/console/schedule\""));
  }

  @Test
  void cronEdit_roundTripsThroughTheRuntimeToggleFile() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/x-www-form-urlencoded")
        .formParam("property", "qlawkus.memory-review.cron")
        .formParam("value", "0 15 4 * * ?")
        .when()
        .put("/api/admin/runtime-toggles")
        .then()
        .statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/runtime-toggles")
        .then().statusCode(200)
        .body("'qlawkus.memory-review.cron'", equalTo("0 15 4 * * ?"));

    given().auth().preemptive().basic(USER, PASS)
        .when()
        .delete("/api/admin/runtime-toggles?property=qlawkus.memory-review.cron")
        .then()
        .statusCode(204);
  }
}
