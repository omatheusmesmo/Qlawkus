package dev.omatheusmesmo.qlawkus.it.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Feedback for every action goes through shared parts: a toast region in the layout, an in-flight state on
 * the control that was used, and server errors tied to the field that caused them. The behavior is checked
 * in a browser by `browser/feedback-check.mjs`.
 */
class FeedbackComponentsTest {

  private static final Path RESOURCES = Path.of("../../console/runtime/src/main/resources");

  private static String read(String relative) {
    try {
      return Files.readString(RESOURCES.resolve(relative));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  @Test
  void theLayoutHoldsOneAnnouncedToastRegion() {
    String layout = read("templates/console/base.html");
    assertTrue(layout.contains("id=\"qk-toasts\""), "the layout carries the toast region");
    assertTrue(layout.contains("aria-live=\"polite\""), "toasts are announced politely");
    assertTrue(layout.contains("role=\"region\""), "the region is a named landmark");
    assertTrue(layout.contains("aria-label=\"Notifications\""));
  }

  @Test
  void theSharedScriptOffersToastsAndBusyStateToEveryPage() {
    String script = read("META-INF/resources/console/console.js");
    assertTrue(script.contains("window.qlawkusToast"), "plain scripts can raise a toast");
    assertTrue(script.contains("htmx:beforeRequest"), "a request marks its control as busy");
    assertTrue(script.contains("aria-busy"), "the busy control is exposed to assistive technology");
    assertFalse(script.contains("innerHTML"), "messages are set as text, never markup");
  }

  @Test
  void aRejectedFormFieldIsMarkedDescribedAndFocused() {
    String script = read("META-INF/resources/console/console.js");
    assertTrue(script.contains("aria-invalid"), "the field that was rejected is flagged");
    assertTrue(script.contains("aria-describedby"), "the field points at the message that explains it");
    assertTrue(script.contains(".focus()"), "focus lands on the first invalid field");
  }

  @Test
  void theStylesheetStylesToastsBusyControlsAndInvalidFields() {
    String css = read("META-INF/resources/console/app.css");
    for (String selector : List.of(".toasts {", ".toast {", ".btn.is-busy", "[aria-invalid=\"true\"]", "@keyframes qk-spin")) {
      assertTrue(css.contains(selector), "app.css lacks " + selector);
    }
    assertTrue(css.contains("prefers-reduced-motion"), "the spinner and toast motion honor reduced motion");
  }

  @Test
  void scriptDrivenPagesRaiseToastsToo() {
    for (String name : List.of("mcp.js", "specialists.js")) {
      assertTrue(read("META-INF/resources/console/" + name).contains("qlawkusToast("),
          name + " reports its outcome through a toast");
    }
  }
}
