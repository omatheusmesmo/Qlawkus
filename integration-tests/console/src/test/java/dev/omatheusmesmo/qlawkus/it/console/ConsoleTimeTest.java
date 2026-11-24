package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

/**
 * Timestamps reach the owner as {@code <time>} elements: the ISO value for machines, the exact local reading
 * in the zone the page names as the title and the fallback text, and a relative label that {@code time.js}
 * puts in place. Raw ISO strings are never visible text.
 */
@QuarkusTest
class ConsoleTimeTest {

  private static final String STARTED = "2026-10-01T12:00:00Z";

  @Inject
  DynamicTaskScheduler tasks;

  @Test
  void theLastRedeployShowsItsTimesAsTimeElementsInTheConfiguredZone() {
    reportRedeploy();
    String exact = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
        .format(Instant.parse(STARTED).atZone(tasks.zone()));

    String page = get("/console/schedule");

    assertThat(page, containsString("<time data-relative datetime=\"" + STARTED + "\" title=\"" + exact + " ("
        + tasks.zone().getId() + ")\">" + exact + "</time>"));
    assertThat(page, not(containsString(">" + STARTED + "<")));
  }

  @Test
  void theOverviewRedeployCardUsesTimeElementsToo() {
    reportRedeploy();

    String card = get("/console/overview/redeploy");

    assertThat(card, containsString("<time data-relative datetime=\""));
    assertThat(card, not(matchesPattern("(?s).*>\\d{4}-\\d{2}-\\d{2}T[^<]*<.*")));
  }

  @Test
  void theUpcomingCardShowsEachRunAsATimeElement() {
    String card = get("/console/overview/upcoming");

    assertThat(card, containsString("<span class=\"upcoming-when\"><time data-relative datetime=\""));
  }

  @Test
  void theBackgroundJobTableShowsItsNextRunsAsTimeElements() {
    String page = get("/console/schedule");

    assertThat(page, matchesPattern("(?s).*data-label=\"Next run\"><time data-relative datetime=\".*"));
  }

  @Test
  void everyPageLoadsTheScriptThatTurnsThemRelative() {
    assertThat(get("/console/schedule"), containsString("src=\"/console/time.js\""));
  }

  @Test
  void theScriptWritesOnlyTextAndRunsAgainAfterEveryHtmxSwap() {
    String source = get("/console/time.js");

    assertThat(source, containsString("Intl.RelativeTimeFormat"));
    assertThat(source, containsString("htmx:afterSwap"));
    assertThat(source, containsString("textContent"));
    assertThat(source, not(containsString("innerHTML")));
  }

  private void reportRedeploy() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .contentType("application/json")
        .body("{\"startedAt\":\"" + STARTED + "\",\"outcome\":\"success\",\"phases\":{\"fetch\":120}}")
        .when().post("/api/admin/composition/redeploys")
        .then().statusCode(201);
  }

  private static String get(String path) {
    return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
        .then().statusCode(200).extract().asString();
  }
}
