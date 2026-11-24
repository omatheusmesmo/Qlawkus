package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The console follows the system theme by default and lets the owner pin light or dark. The choice is applied
 * by a script in the head before the stylesheet paints, so a reload never flashes the wrong theme. Behavior is
 * checked in a browser by {@code browser/a11y-check.mjs}.
 */
@QuarkusTest
class ThemeToggleTest {

  private static final Path CSS = Path.of("../../console/runtime/src/main/resources/META-INF/resources/console/app.css");

  private static String page() {
    return given().auth().preemptive().basic("qlawkus", "qlawkus-test").when().get("/console/memory").then()
        .extract().asString();
  }

  private static String script(String name) {
    return given().auth().preemptive().basic("qlawkus", "qlawkus-test").when().get("/console/" + name).then()
        .extract().asString();
  }

  @Test
  void aSynchronousHeadScriptAppliesTheStoredThemeBeforeTheStylesheet() {
    String html = page();
    int theme = html.indexOf("src=\"/console/theme.js\"");
    int stylesheet = html.indexOf("/console/app.css");
    assertTrue(theme > 0, "the head loads theme.js");
    assertTrue(theme < stylesheet, "it runs before the stylesheet is requested");
    assertFalse(html.substring(theme, html.indexOf(">", theme)).contains("defer"), "it must not be deferred");
    assertFalse(html.contains("<script>"), "the console serves no inline script");
    String source = script("theme.js");
    assertTrue(source.contains("localStorage.getItem(\"qlawkus-theme\")"), "the choice is read from localStorage");
    assertTrue(source.contains("data-theme"), "the stored choice is applied as data-theme");
  }

  @Test
  void theSidebarOffersSystemLightAndDark() {
    String html = page();
    assertTrue(html.contains("role=\"radiogroup\""), "the three choices are one radio group");
    assertTrue(html.contains("aria-label=\"Theme\""), "the group is named");
    for (String choice : new String[] {"system", "light", "dark"}) {
      assertTrue(html.contains("data-theme-choice=\"" + choice + "\""), "no " + choice + " choice");
    }
  }

  @Test
  void theToggleScriptPersistsAndNeverTouchesMarkup() {
    String source = script("console.js");
    assertTrue(source.contains("data-theme-choice"), "console.js drives the toggle");
    assertTrue(source.contains("localStorage.setItem"), "the choice persists");
    assertTrue(source.contains("aria-checked"), "the selected choice is exposed");
    assertFalse(source.contains("innerHTML"));
  }

  @Test
  void everyInteractiveElementShowsOneFocusRing() {
    String css;
    try {
      css = Files.readString(CSS);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    assertTrue(css.contains(":is(a, button, input, select, textarea, summary, [tabindex]):focus-visible"),
        "a single rule covers every interactive element");
    assertTrue(css.contains("outline: 2px solid var(--accent)"), "the ring uses the accent");
  }
}
