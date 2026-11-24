package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The structure the config editor's client-side filtering depends on: one collapsible section per
 * root with an anchor and counts, rows carrying the phase and changed markers, and a result slot
 * beside every row. The behavior of the script itself is checked in a browser by
 * {@code browser/config-check.mjs}.
 */
@QuarkusTest
class ConfigEditorFilterTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String RUN_TIME_PROPERTY = "qlawkus.memory-curation.enabled";
  private static final String BUILD_TIME_PROPERTY = "qlawkus.composition.manifest";
  private static final Pattern ROW = Pattern.compile("<tr class=\"cfg-item\"([^>]*)>(.*?)</tr>", Pattern.DOTALL);
  private static final Pattern SECTION = Pattern.compile("<details class=\"panel cfg-section\"([^>]*)>(.*?)</details>",
      Pattern.DOTALL);
  private static final Pattern SOURCE = Pattern.compile("class=\"cfg-src\"[^>]*>([^<]*)<");

  private String page() {
    return given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/config")
        .then().statusCode(200)
        .extract().asString();
  }

  private static List<MatchResult> all(Pattern pattern, String html) {
    return pattern.matcher(html).results().toList();
  }

  @Test
  void everyRootIsACollapsibleSectionWithAnAnchorAndCounts() {
    List<MatchResult> sections = all(SECTION, page());
    assertTrue(sections.size() > 10, "one section per documented config root");
    Set<String> anchors = new HashSet<>();
    for (MatchResult section : sections) {
      Matcher id = Pattern.compile("id=\"(cfg-[a-z0-9-]+)\"").matcher(section.group(1));
      assertTrue(id.find(), "section without an anchor: " + section.group(1));
      assertTrue(anchors.add(id.group(1)), "duplicate anchor " + id.group(1));
      assertTrue(section.group(2).contains("class=\"cfg-count\""), "section without a count badge");
      assertTrue(section.group(2).contains("<summary"), "section without a summary");
    }
  }

  @Test
  void onlySectionsHoldingAnOwnerOverrideStartOpen() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"false\"}".formatted(RUN_TIME_PROPERTY))
        .when().put("/api/admin/runtime-toggles")
        .then().statusCode(204);
    try {
      int opened = 0;
      int sections = 0;
      for (MatchResult section : all(SECTION, page())) {
        sections++;
        boolean open = section.group(1).contains(" open");
        boolean hasOwnerSet = section.group(2).contains("data-owner=\"true\"");
        assertEquals(hasOwnerSet, open, "open must follow the owner overrides: " + section.group(1));
        if (open) {
          opened++;
        }
      }
      assertTrue(opened >= 1, "the section of the overridden property starts open");
      assertTrue(opened * 3 < sections, "most sections start closed, or the page opens many screens tall");
    } finally {
      given().auth().preemptive().basic(USER, PASS)
          .when().delete("/api/admin/runtime-toggles?property=" + RUN_TIME_PROPERTY)
          .then().statusCode(204);
    }
  }

  @Test
  void aValueFromTheDistributionBaselineIsChangedButIsNotAnOwnerOverride() {
    boolean found = false;
    for (MatchResult row : all(ROW, page())) {
      Matcher source = SOURCE.matcher(row.group(2));
      assertTrue(source.find(), "row without a source label");
      if ("application.properties".equals(source.group(1))) {
        found = true;
        assertTrue(row.group(1).contains("data-changed=\"true\""), "a non-default value is changed: " + row.group(1));
        assertFalse(row.group(1).contains("data-owner=\"true\""),
            "the distribution's own application.properties is the baseline, not the owner's override: " + row.group(1));
      }
    }
    assertTrue(found, "the test app sets properties in application.properties");
  }

  @Test
  void aRuntimeToggleMarksItsRowAsOwnerSet() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"false\"}".formatted(RUN_TIME_PROPERTY))
        .when().put("/api/admin/runtime-toggles")
        .then().statusCode(204);
    try {
      String row = rowOf(page(), RUN_TIME_PROPERTY);
      assertTrue(row.contains("data-owner=\"true\""), row);
      assertTrue(row.contains("data-changed=\"true\""), row);
    } finally {
      given().auth().preemptive().basic(USER, PASS)
          .when().delete("/api/admin/runtime-toggles?property=" + RUN_TIME_PROPERTY)
          .then().statusCode(204);
    }
  }

  @Test
  void aRowIsChangedWhenItsSourceIsNotTheDefaultOrAnOverrideIsPending() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"false\"}".formatted(RUN_TIME_PROPERTY))
        .when().put("/api/admin/runtime-toggles")
        .then().statusCode(204);
    try {
      int changed = 0;
      int unchanged = 0;
      for (MatchResult row : all(ROW, page())) {
        Matcher source = SOURCE.matcher(row.group(2));
        assertTrue(source.find(), "row without a source label");
        boolean isDefault = "default".equals(source.group(1));
        boolean pending = row.group(2).contains("class=\"pill cfg-pending\"");
        boolean markedChanged = row.group(1).contains("data-changed=\"true\"");
        assertEquals(!isDefault || pending, markedChanged, "row " + row.group(1) + " source " + source.group(1));
        if (pending) {
          assertTrue(row.group(2).contains("override file, after restart"), "the pill says what the override waits for");
        }
        if (markedChanged) {
          changed++;
        } else {
          unchanged++;
        }
      }
      assertTrue(changed >= 1 && unchanged >= 1, "the page must hold both kinds of row");
      assertTrue(rowOf(page(), RUN_TIME_PROPERTY).contains("data-changed=\"true\""),
          "a saved override is changed even though the running value moves only after a restart");
    } finally {
      given().auth().preemptive().basic(USER, PASS)
          .when().delete("/api/admin/runtime-toggles?property=" + RUN_TIME_PROPERTY)
          .then().statusCode(204);
    }
  }

  @Test
  void aRowCarriesThePhaseTheFilterUses() {
    String html = page();
    assertTrue(rowOf(html, RUN_TIME_PROPERTY).contains("data-phase=\"run\""));
    assertTrue(rowOf(html, BUILD_TIME_PROPERTY).contains("data-phase=\"build\""));
  }

  @Test
  void theToolbarOffersSearchChangedAndPhaseFilters() {
    String html = page();
    assertTrue(html.contains("id=\"cfg-search\""));
    assertTrue(html.contains("id=\"cfg-changed\""));
    assertTrue(html.contains("id=\"cfg-phase\""));
    assertTrue(html.contains("class=\"cfg-index\""));
    assertTrue(html.contains("src=\"/console/config.js\""));
    assertTrue(html.contains("id=\"cfg-status\""));
  }

  @Test
  void everyRowHasItsOwnResultSlotAndTheOutcomeGoesThere() {
    String html = page();
    List<MatchResult> rows = all(ROW, html);
    assertTrue(rows.size() > 50, "the page lists every documented property");
    for (MatchResult row : rows) {
      assertEquals(1, Pattern.compile("class=\"result cfg-row-result\"").matcher(row.group(2)).results().count(),
          "one result slot per row");
      assertFalse(row.group(2).contains("hx-target=\"#cfg-result\""), "the outcome is shown beside the row");
    }
  }

  private static String rowOf(String html, String property) {
    for (MatchResult row : all(ROW, html)) {
      if (row.group(2).contains(">" + property + "<")) {
        return row.group(1);
      }
    }
    throw new AssertionError("no row for " + property);
  }
}
