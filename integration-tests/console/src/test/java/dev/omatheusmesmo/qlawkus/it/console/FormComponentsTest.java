package dev.omatheusmesmo.qlawkus.it.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Forms and buttons across the console share one set of components (`.field`, `.input`, `.select`,
 * `.textarea`, `.btn`) instead of a rule set per page. The look is checked in a browser by
 * `browser/forms-check.mjs`.
 */
class FormComponentsTest {

  private static final Path CONSOLE = Path.of("../../console/runtime/src/main/resources");
  private static final Path SKILL_HUB = Path.of("../../console-skill-hub/runtime/src/main/resources");
  private static final List<Path> PAGES = List.of(
      CONSOLE.resolve("templates/console/specialists.html"), CONSOLE.resolve("templates/console/mcp.html"),
      CONSOLE.resolve("templates/console/config.html"), CONSOLE.resolve("templates/console/schedule.html"),
      CONSOLE.resolve("templates/console/skills.html"), CONSOLE.resolve("templates/console/cognition.html"),
      CONSOLE.resolve("templates/console/memory.html"), CONSOLE.resolve("templates/console/setup.html"),
      CONSOLE.resolve("templates/console/chat.html"),
      SKILL_HUB.resolve("templates/console/skill-install.html"));
  private static final Set<String> NOT_TEXT_ENTRY = Set.of("hidden", "checkbox", "radio", "file", "submit", "button");
  private static final Pattern TAG = Pattern.compile("<(input|select|textarea|button)\\b([^>]*)>", Pattern.DOTALL);
  private static final Pattern TYPE = Pattern.compile("\\btype=\"([a-z]+)\"");
  private static final Pattern CLASS = Pattern.compile("\\bclass=\"([^\"]*)\"");
  private static final Set<String> OWN_STYLE = Set.of("nav-toggle");

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException unreadable) {
      throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), unreadable);
    }
  }

  private static String script(String name) {
    return read(CONSOLE.resolve("META-INF/resources/console/" + name));
  }

  private static boolean hasClass(String attributes, String name) {
    Matcher classes = CLASS.matcher(attributes);
    return classes.find() && List.of(classes.group(1).split("\\s+")).contains(name);
  }

  @Test
  void everyTextControlUsesTheSharedFieldClasses() {
    int controls = 0;
    for (Path page : PAGES) {
      Matcher tags = TAG.matcher(read(page));
      while (tags.find()) {
        String kind = tags.group(1);
        String attributes = tags.group(2);
        if (kind.equals("button")) {
          continue;
        }
        Matcher type = TYPE.matcher(attributes);
        if (kind.equals("input") && type.find() && NOT_TEXT_ENTRY.contains(type.group(1))) {
          continue;
        }
        controls++;
        String expected = kind.equals("input") ? "input" : kind;
        assertTrue(hasClass(attributes, expected), page + ": <" + kind + attributes + "> lacks the ." + expected + " class");
      }
    }
    assertTrue(controls >= 20, "the pages hold text controls: " + controls);
  }

  @Test
  void everyButtonUsesTheSharedButtonClasses() {
    int buttons = 0;
    for (Path page : PAGES) {
      Matcher tags = TAG.matcher(read(page));
      while (tags.find()) {
        if (!tags.group(1).equals("button")) {
          continue;
        }
        String attributes = tags.group(2);
        if (OWN_STYLE.stream().anyMatch(attributes::contains)) {
          continue;
        }
        buttons++;
        assertTrue(hasClass(attributes, "btn"), page + ": <button" + attributes + "> lacks the .btn class");
      }
    }
    assertTrue(buttons >= 10, "the pages hold buttons: " + buttons);
  }

  @Test
  void buttonsBuiltByScriptsUseTheSameClasses() {
    String mcp = script("mcp.js");
    assertTrue(mcp.contains("\"btn btn-"), "mcp.js must give the buttons it builds the shared classes");
    assertFalse(mcp.contains("classList.add(\"mcp-danger\")"), "the danger look is the shared .btn-danger");
  }

  @Test
  void theStylesheetDefinesTheComponentsOnceAndNoPageRepeatsThem() {
    String css = script("app.css");
    for (String selector : List.of(".field {", ".field-label {", ".field-hint {", ".input,", ".textarea", ".actions {")) {
      assertTrue(css.contains(selector), "app.css lacks " + selector);
    }
    for (String duplicate : List.of(".mcp-editor input", ".mcp-editor textarea", ".mcp-editor select",
        ".specialist-editor textarea", ".form input[", ".cfg-row input[", ".mcp-actions .mcp-danger")) {
      assertFalse(css.contains(duplicate), "a per-page field rule survives: " + duplicate);
    }
  }

  @Test
  void controlsNeverOverflowTheirColumnOnSmallScreens() {
    String css = script("app.css");
    Matcher input = Pattern.compile("^\\.input,[^{]*\\{([^}]*)\\}", Pattern.DOTALL | Pattern.MULTILINE).matcher(css);
    assertTrue(input.find(), "the shared control rule exists");
    String rule = input.group(1);
    assertTrue(rule.contains("width: 100%"), "a control fills its column: " + rule);
    assertTrue(rule.contains("min-width: 0"), "a control can shrink inside a flex or grid row: " + rule);
  }
}
