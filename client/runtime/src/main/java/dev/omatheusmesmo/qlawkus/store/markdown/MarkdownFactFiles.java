package dev.omatheusmesmo.qlawkus.store.markdown;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * File operations for the markdown fact backend. Each fact is a {@code <root>/<id>.md} file with a
 * small YAML frontmatter ({@code source}, {@code createdAt}, ...) followed by the fact text as the
 * body. The id is the MD5 of the content, so identical facts deduplicate. A sibling
 * {@code .embeddings.json} caches the embedding vector per id, so restarts re-embed only changed
 * facts. The files are the source of truth; the cache is derived and safe to delete.
 */
public class MarkdownFactFiles {

  private static final String CACHE_FILE = ".embeddings.json";
  private static final String DELIMITER = "---";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path root;

  public MarkdownFactFiles(Path root) {
    this.root = root;
  }

  public record FactRecord(String id, String content, Map<String, String> metadata) {
  }

  public List<FactRecord> loadAll() {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<FactRecord> facts = new ArrayList<>();
    try (Stream<Path> files = Files.list(root)) {
      files.filter(p -> p.getFileName().toString().endsWith(".md"))
          .forEach(p -> readFact(p, facts));
    } catch (IOException e) {
      Log.warnf(e, "Failed to list fact root %s", root);
    }
    return facts;
  }

  private void readFact(Path file, List<FactRecord> out) {
    try {
      Parsed parsed = parse(Files.readString(file, StandardCharsets.UTF_8));
      String id = file.getFileName().toString().replaceFirst("\\.md$", "");
      out.add(new FactRecord(id, parsed.body(), parsed.frontmatter()));
    } catch (IOException e) {
      Log.warnf(e, "Failed to read fact %s", file);
    }
  }

  public boolean exists(String id) {
    return Files.isRegularFile(root.resolve(id + ".md"));
  }

  public void write(String id, String content, Map<String, String> metadata) {
    try {
      Files.createDirectories(root);
      Files.writeString(root.resolve(id + ".md"), render(content, metadata),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write fact " + id, e);
    }
  }

  public boolean delete(String id) {
    try {
      return Files.deleteIfExists(root.resolve(id + ".md"));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete fact " + id, e);
    }
  }

  public List<String> listSources() {
    TreeSet<String> sources = new TreeSet<>();
    for (FactRecord fact : loadAll()) {
      String source = fact.metadata().get("source");
      if (source != null && !source.isBlank()) {
        sources.add(source);
      }
    }
    return new ArrayList<>(sources);
  }

  public Map<String, float[]> loadCache() {
    Path file = root.resolve(CACHE_FILE);
    if (!Files.isRegularFile(file)) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, float[]> cache = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
          new TypeReference<LinkedHashMap<String, float[]>>() {});
      return cache == null ? new LinkedHashMap<>() : cache;
    } catch (IOException e) {
      Log.warnf(e, "Failed to read embedding cache %s; will re-embed", file);
      return new LinkedHashMap<>();
    }
  }

  public void saveCache(Map<String, float[]> cache) {
    try {
      Files.createDirectories(root);
      Files.writeString(root.resolve(CACHE_FILE), MAPPER.writeValueAsString(cache),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      Log.warnf(e, "Failed to write embedding cache");
    }
  }

  public static String md5(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }

  private String render(String content, Map<String, String> metadata) {
    StringBuilder sb = new StringBuilder();
    sb.append(DELIMITER).append('\n');
    Map<String, String> frontmatter = new LinkedHashMap<>(metadata);
    frontmatter.putIfAbsent("createdAt", Instant.now().toString());
    frontmatter.forEach((key, value) ->
        sb.append(key).append(": ").append(value == null ? "" : value).append('\n'));
    sb.append(DELIMITER).append("\n\n");
    sb.append(content == null ? "" : content.strip()).append('\n');
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
}
