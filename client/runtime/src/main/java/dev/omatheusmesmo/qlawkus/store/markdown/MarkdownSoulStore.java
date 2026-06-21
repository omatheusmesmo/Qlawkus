package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Markdown-backed {@link SoulStore}, active when {@code qlawkus.cognition.backend=markdown}. The
 * persona is a single {@code <root>/soul.md} file: YAML frontmatter ({@code name}, {@code mood})
 * over a body whose core identity and current state are split by a sentinel comment (the identity
 * itself contains {@code ##} headers, so a sentinel is safer than header-splitting). With no Flyway
 * to seed it, the first {@link #load} copies the bundled default persona
 * ({@code META-INF/qlawkus/default-soul.md}) so markdown mode still boots with a persona.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "markdown")
public class MarkdownSoulStore implements SoulStore {

  private static final String FILE = "soul.md";
  private static final String DEFAULT_RESOURCE = "META-INF/qlawkus/default-soul.md";
  private static final String DELIMITER = "---";
  private static final String STATE_SENTINEL = "<!-- qlawkus:current-state -->";

  private final Path root;

  @Inject
  public MarkdownSoulStore(AgentConfig config) {
    this(config.state().root());
  }

  public MarkdownSoulStore(String root) {
    this.root = Path.of(root);
  }

  @Override
  public Soul load() {
    Path file = root.resolve(FILE);
    String content;
    if (Files.isRegularFile(file)) {
      content = read(file);
    } else {
      content = defaultContent();
      write(file, content);
    }
    return parse(content);
  }

  @Override
  public void save(Soul soul) {
    write(root.resolve(FILE), render(soul));
  }

  private Soul parse(String raw) {
    Soul soul = new Soul();
    soul.id = 1L;
    soul.mood = Mood.FOCUSED;
    String text = raw.strip();
    String body = text;
    if (text.startsWith(DELIMITER)) {
      int end = text.indexOf("\n" + DELIMITER, DELIMITER.length());
      if (end >= 0) {
        String header = text.substring(DELIMITER.length(), end).strip();
        body = text.substring(end + ("\n" + DELIMITER).length()).strip();
        for (String line : header.split("\n")) {
          int colon = line.indexOf(':');
          if (colon <= 0) {
            continue;
          }
          String key = line.substring(0, colon).strip();
          String value = line.substring(colon + 1).strip();
          if (key.equals("name")) {
            soul.name = value;
          } else if (key.equals("mood")) {
            soul.mood = parseMood(value);
          }
        }
      }
    }
    int sentinel = body.indexOf(STATE_SENTINEL);
    if (sentinel >= 0) {
      soul.coreIdentity = body.substring(0, sentinel).strip();
      soul.currentState = body.substring(sentinel + STATE_SENTINEL.length()).strip();
    } else {
      soul.coreIdentity = body;
      soul.currentState = "";
    }
    return soul;
  }

  private String render(Soul soul) {
    return DELIMITER + "\n"
        + "name: " + (soul.name == null ? "" : soul.name) + "\n"
        + "mood: " + (soul.mood == null ? Mood.FOCUSED : soul.mood) + "\n"
        + DELIMITER + "\n"
        + (soul.coreIdentity == null ? "" : soul.coreIdentity.strip()) + "\n\n"
        + STATE_SENTINEL + "\n"
        + (soul.currentState == null ? "" : soul.currentState.strip()) + "\n";
  }

  private static Mood parseMood(String value) {
    try {
      return Mood.valueOf(value.toUpperCase());
    } catch (RuntimeException e) {
      return Mood.FOCUSED;
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + file, e);
    }
  }

  private void write(Path file, String content) {
    try {
      Files.createDirectories(root);
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + file, e);
    }
  }

  private String defaultContent() {
    try (InputStream in = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(DEFAULT_RESOURCE)) {
      if (in == null) {
        Log.warnf("Default persona resource %s not found; seeding minimal persona", DEFAULT_RESOURCE);
        return DELIMITER + "\nname: Qlawkus\nmood: FOCUSED\n" + DELIMITER + "\nQlawkus.\n\n"
            + STATE_SENTINEL + "\nAwaiting first interaction.\n";
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read default persona resource", e);
    }
  }
}
