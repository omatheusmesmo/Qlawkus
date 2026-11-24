package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every destructive action in the console asks through the one {@code <dialog>} in the shared layout,
 * never through the browser's native {@code confirm}. The behavior of the dialog itself is checked in a
 * browser by {@code browser/confirm-check.mjs}.
 */
@QuarkusTest
class ConfirmationDialogTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final List<String> PAGES = List.of("/console/memory", "/console/skills", "/console/config",
      "/console/schedule", "/console/specialists", "/console/mcp", "/console/cognition", "/console/skills/install");
  private static final Pattern DELETE_ELEMENT = Pattern.compile("<(?:button|a)\\b[^>]*\\bhx-delete=[^>]*>", Pattern.DOTALL);
  private static final Pattern MIGRATE_ELEMENT = Pattern.compile("<button\\b[^>]*hx-post=\"[^\"]*cognition/migrate[^>]*>");

  private static String get(String path) {
    return given().auth().preemptive().basic(USER, PASS).when().get(path).then().extract().asString();
  }

  private static boolean exists(String path) {
    return given().auth().preemptive().basic(USER, PASS).when().get(path).statusCode() == 200;
  }

  @Test
  void theSharedLayoutCarriesTheDialogEveryPageUses() {
    String html = get("/console/memory");
    assertTrue(html.contains("<dialog"), "the layout holds the confirmation dialog");
    assertTrue(html.contains("id=\"qk-confirm\""));
    assertTrue(html.contains("id=\"qk-confirm-target\""), "the dialog names the target");
    assertTrue(html.contains("id=\"qk-confirm-body\""), "the dialog states the consequence");
    assertTrue(html.contains("id=\"qk-confirm-ok\""));
    assertTrue(html.contains("src=\"/console/confirm.js\""));
  }

  @Test
  void noPageAsksThroughTheNativeConfirm() {
    int checked = 0;
    for (String page : PAGES) {
      if (!exists(page)) {
        continue;
      }
      checked++;
      assertFalse(get(page).contains("hx-confirm"), page + " still uses hx-confirm");
    }
    assertTrue(checked >= 4, "the console pages that carry destructive actions must be reachable");
  }

  @Test
  void everyDeleteControlDeclaresWhatItRemovesAndWhatFollows() {
    int controls = 0;
    for (String page : PAGES) {
      if (!exists(page)) {
        continue;
      }
      for (MatchResult element : DELETE_ELEMENT.matcher(get(page)).results().toList()) {
        controls++;
        String tag = element.group();
        assertTrue(tag.contains("data-confirm=\""), page + ": a delete without a consequence: " + tag);
        assertTrue(tag.contains("data-confirm-target=\""), page + ": a delete that does not name its target: " + tag);
        assertTrue(tag.contains("data-confirm-action=\""), page + ": a delete without an action label: " + tag);
      }
    }
    assertTrue(controls >= 3, "the pages hold delete controls: " + controls);
  }

  @Test
  void aMigrationThatOverwritesAStoreAsksFirst() {
    if (!exists("/console/cognition")) {
      return;
    }
    List<MatchResult> buttons = MIGRATE_ELEMENT.matcher(get("/console/cognition")).results().toList();
    if (buttons.isEmpty()) {
      return;
    }
    assertTrue(buttons.size() == 2, "both migration directions are offered");
    for (MatchResult button : buttons) {
      assertTrue(button.group().contains("data-confirm=\""), button.group());
      assertTrue(button.group().contains("data-confirm-target=\""), button.group());
    }
  }

  @Test
  void theScriptsThatRemoveThingsAskThroughTheDialog() {
    for (String script : List.of("/console/specialists.js", "/console/mcp.js")) {
      String source = get(script);
      assertFalse(source.contains("window.confirm") || source.contains(" confirm("), script + " uses the native confirm");
      assertTrue(source.contains("qlawkusConfirm("), script + " must ask through the dialog");
    }
  }

  @Test
  void theConfirmScriptInterceptsHtmxAndOffersAPromiseForPlainScripts() {
    String source = get("/console/confirm.js");
    assertTrue(source.contains("htmx:confirm"), "htmx requests are intercepted before they are sent");
    assertTrue(source.contains("window.qlawkusConfirm"), "plain scripts get the same dialog");
    assertTrue(source.contains("showModal"), "a modal dialog traps focus and closes on Escape");
    assertFalse(source.contains("innerHTML"), "the target and consequence are text, never markup");
  }
}
