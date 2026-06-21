package dev.omatheusmesmo.qlawkus.store.markdown;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * File operations for the markdown episodic backend. Each journal is a {@code <root>/<date>.md} file
 * with a small YAML frontmatter ({@code date}, {@code messageCount}, {@code createdAt}) followed by
 * the day's summary as the body. The date is the filename, so there is naturally one journal per day
 * and a second consolidation of the same day is a no-op. Unlike the fact backend there is no
 * embedding cache here: the journal text is embedded through the {@code FactStore}
 * ({@code source=episodic-consolidator}), which owns its own index.
 */
public class MarkdownEpisodicFiles {

  private static final String DELIMITER = "---";

  private final Path root;

  public MarkdownEpisodicFiles(Path root) {
    this.root = root;
  }

  public record JournalRecord(LocalDate date, String summary, int messageCount, Instant createdAt) {
  }

  public boolean exists(LocalDate date) {
    return Files.isRegularFile(root.resolve(date + ".md"));
  }

  public void write(LocalDate date, String summary, int messageCount) {
    try {
      Files.createDirectories(root);
      Files.writeString(root.resolve(date + ".md"), render(date, summary, messageCount),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write journal " + date, e);
    }
  }

  public List<JournalRecord> loadAll() {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<JournalRecord> journals = new ArrayList<>();
    try (Stream<Path> files = Files.list(root)) {
      files.filter(p -> p.getFileName().toString().endsWith(".md"))
          .forEach(p -> readJournal(p, journals));
    } catch (IOException e) {
      Log.warnf(e, "Failed to list journal root %s", root);
    }
    journals.sort((a, b) -> a.date().compareTo(b.date()));
    return journals;
  }

  public long count() {
    return loadAll().size();
  }

  public long deleteAll() {
    List<JournalRecord> all = loadAll();
    for (JournalRecord journal : all) {
      try {
        Files.deleteIfExists(root.resolve(journal.date() + ".md"));
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to delete journal " + journal.date(), e);
      }
    }
    return all.size();
  }

  private void readJournal(Path file, List<JournalRecord> out) {
    try {
      Parsed parsed = parse(Files.readString(file, StandardCharsets.UTF_8));
      String name = file.getFileName().toString().replaceFirst("\\.md$", "");
      LocalDate date = parseDate(parsed.frontmatter().get("date"), name);
      int messageCount = parseInt(parsed.frontmatter().get("messageCount"));
      Instant createdAt = parseInstant(parsed.frontmatter().get("createdAt"));
      out.add(new JournalRecord(date, parsed.body(), messageCount, createdAt));
    } catch (IOException e) {
      Log.warnf(e, "Failed to read journal %s", file);
    }
  }

  private String render(LocalDate date, String summary, int messageCount) {
    StringBuilder sb = new StringBuilder();
    sb.append(DELIMITER).append('\n');
    Map<String, String> frontmatter = new LinkedHashMap<>();
    frontmatter.put("date", date.toString());
    frontmatter.put("messageCount", Integer.toString(messageCount));
    frontmatter.put("createdAt", Instant.now().toString());
    frontmatter.forEach((key, value) -> sb.append(key).append(": ").append(value).append('\n'));
    sb.append(DELIMITER).append("\n\n");
    sb.append(summary == null ? "" : summary.strip()).append('\n');
    return sb.toString();
  }

  private record Parsed(Map<String, String> frontmatter, String body) {
  }

  private Parsed parse(String raw) {
    Map<String, String> frontmatter = new LinkedHashMap<>();
    String text = raw.strip();
    if (!text.startsWith(DELIMITER)) {
      return new Parsed(frontmatter, text);
    }
    int end = text.indexOf("\n" + DELIMITER, DELIMITER.length());
    if (end < 0) {
      return new Parsed(frontmatter, text);
    }
    String header = text.substring(DELIMITER.length(), end).strip();
    String body = text.substring(end + ("\n" + DELIMITER).length()).strip();
    for (String line : header.split("\n")) {
      int colon = line.indexOf(':');
      if (colon > 0) {
        frontmatter.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
      }
    }
    return new Parsed(frontmatter, body);
  }

  private static LocalDate parseDate(String value, String fallback) {
    try {
      return LocalDate.parse(value == null || value.isBlank() ? fallback : value);
    } catch (RuntimeException e) {
      return LocalDate.parse(fallback);
    }
  }

  private static int parseInt(String value) {
    try {
      return value == null || value.isBlank() ? 0 : Integer.parseInt(value.strip());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static Instant parseInstant(String value) {
    try {
      return value == null || value.isBlank() ? null : Instant.parse(value.strip());
    } catch (RuntimeException e) {
      return null;
    }
  }
}
