package dev.omatheusmesmo.qlawkus.store.markdown;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * File operations for the markdown working-memory backend. Each conversation is an append-only
 * {@code <root>/<memoryId>.jsonl} log; every line is {@code <ISO-8601 instant>\t<message JSON>},
 * where the JSON is langchain4j's {@code ChatMessageSerializer} form. The per-line timestamp lets
 * the episodic consolidator slice a day's messages without a database, and append-only writes
 * preserve the original {@code createdAt} of earlier messages (the consolidator relies on it).
 */
public class MarkdownWorkingMemoryFiles {

  private static final char SEP = '\t';

  private final Path root;

  public MarkdownWorkingMemoryFiles(Path root) {
    this.root = root;
  }

  public record StoredMessage(Instant createdAt, String messageJson) {
  }

  private Path fileFor(String memoryId) {
    return root.resolve(sanitize(memoryId) + ".jsonl");
  }

  static String sanitize(String memoryId) {
    return memoryId == null ? "default" : memoryId.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  public List<StoredMessage> read(String memoryId) {
    return readFile(fileFor(memoryId));
  }

  public boolean exists(String memoryId) {
    return Files.isRegularFile(fileFor(memoryId));
  }

  /** Appends new messages with the current timestamp, preserving existing lines. */
  public void append(String memoryId, List<String> messageJsons) {
    Path file = fileFor(memoryId);
    StringBuilder sb = new StringBuilder();
    Instant now = Instant.now();
    for (String json : messageJsons) {
      sb.append(now).append(SEP).append(json).append('\n');
    }
    try {
      Files.createDirectories(root);
      Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to append working memory " + memoryId, e);
    }
  }

  /** Rewrites the whole log (used when the incoming history diverges from what is stored). */
  public void replace(String memoryId, List<String> messageJsons) {
    Path file = fileFor(memoryId);
    StringBuilder sb = new StringBuilder();
    Instant now = Instant.now();
    for (String json : messageJsons) {
      sb.append(now).append(SEP).append(json).append('\n');
    }
    try {
      Files.createDirectories(root);
      Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write working memory " + memoryId, e);
    }
  }

  public void delete(String memoryId) {
    try {
      Files.deleteIfExists(fileFor(memoryId));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete working memory " + memoryId, e);
    }
  }

  public long deleteAll() {
    if (!Files.isDirectory(root)) {
      return 0;
    }
    long total = count();
    try (Stream<Path> files = Files.list(root)) {
      files.filter(p -> p.getFileName().toString().endsWith(".jsonl")).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new UncheckedIOException("Failed to delete " + p, e);
        }
      });
    } catch (IOException e) {
      Log.warnf(e, "Failed to purge working-memory root %s", root);
    }
    return total;
  }

  public long count() {
    long total = 0;
    for (Path file : allFiles()) {
      total += readFile(file).size();
    }
    return total;
  }

  /** All stored messages across every conversation, for date-range slicing. */
  public List<StoredMessage> readAll() {
    List<StoredMessage> all = new ArrayList<>();
    for (Path file : allFiles()) {
      all.addAll(readFile(file));
    }
    all.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
    return all;
  }

  private List<Path> allFiles() {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(root)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList();
    } catch (IOException e) {
      Log.warnf(e, "Failed to list working-memory root %s", root);
      return List.of();
    }
  }

  private List<StoredMessage> readFile(Path file) {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    List<StoredMessage> messages = new ArrayList<>();
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (line.isBlank()) {
          continue;
        }
        int tab = line.indexOf(SEP);
        if (tab < 0) {
          continue;
        }
        Instant createdAt = parseInstant(line.substring(0, tab));
        messages.add(new StoredMessage(createdAt, line.substring(tab + 1)));
      }
    } catch (IOException e) {
      Log.warnf(e, "Failed to read working memory %s", file);
    }
    return messages;
  }

  private static Instant parseInstant(String value) {
    try {
      return Instant.parse(value.strip());
    } catch (RuntimeException e) {
      return Instant.EPOCH;
    }
  }
}
