package dev.omatheusmesmo.qlawkus.tools.skillhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillFrontmatter;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default {@link SkillHub}: talks to the registry over plain HTTP from Java (native-image friendly,
 * no external CLI). Search hits {@code <base-url>/api/search?q=} and, for each configured
 * {@code well-known-hosts} entry, the {@code /.well-known/agent-skills/index.json} index. Install
 * resolves an {@code owner/repo} slug (or a direct SKILL.md URL) to the raw SKILL.md, parses its
 * frontmatter, and saves it through {@link SkillStore} into the owned root. Publish renders a skill
 * to a SKILL.md under the configured publish directory (no {@code git push} - that stays with the
 * owner on the host).
 *
 * <p>This is a {@link DefaultBean} so an alternative backend can override it without touching
 * callers.
 */
@ApplicationScoped
@DefaultBean
public class HttpSkillHub implements SkillHub {

  private static final Pattern OWNER_REPO = Pattern.compile("^[\\w.-]+/[\\w.-]+$");
  private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  private static final List<String> DEFAULT_BRANCHES = List.of("main", "master");

  private final SkillHubConfig config;
  private final SkillStore skillStore;
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @Inject
  public HttpSkillHub(SkillHubConfig config, SkillStore skillStore) {
    this.config = config;
    this.skillStore = skillStore;
    this.httpClient = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
  }

  @Override
  public List<SkillRef> search(String query, int limit) {
    int cap = Math.min(limit, config.maxResults());
    List<SkillRef> refs = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    RuntimeException firstError = null;

    try {
      addUnique(refs, seen, searchRegistry(query), cap);
    } catch (RuntimeException e) {
      firstError = e;
    }

    for (String host : config.wellKnownHosts().orElse(List.of())) {
      if (refs.size() >= cap) {
        break;
      }
      try {
        addUnique(refs, seen, searchWellKnown(host, query), cap);
      } catch (RuntimeException e) {
        if (firstError == null) {
          firstError = e;
        }
      }
    }

    if (refs.isEmpty() && firstError != null) {
      throw firstError;
    }
    return refs;
  }

  private List<SkillRef> searchRegistry(String query) {
    String url = config.baseUrl().replaceAll("/+$", "")
        + "/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    JsonNode hits = firstArray(readTree(get(url), url), "results", "skills", "data", "items");
    List<SkillRef> refs = new ArrayList<>();
    for (JsonNode node : hits) {
      String name = text(node, "name", "slug", "title");
      String source = text(node, "source", "repo", "full_name", "repository", "url");
      if (!name.isBlank() && !source.isBlank()) {
        refs.add(new SkillRef(name, text(node, "description", "summary"), source));
      }
    }
    return refs;
  }

  private List<SkillRef> searchWellKnown(String host, String query) {
    String base = normalizeWellKnown(host);
    String activePath = "/.well-known/agent-skills/";
    String body = tryGet(base + activePath + "index.json");
    if (body == null) {
      activePath = "/.well-known/skills/";
      body = tryGet(base + activePath + "index.json");
    }
    if (body == null) {
      return List.of();
    }
    JsonNode skills = readTree(body, base).get("skills");
    List<SkillRef> refs = new ArrayList<>();
    if (skills != null && skills.isArray()) {
      for (JsonNode entry : skills) {
        String url = text(entry, "url");
        if (url.isBlank()) {
          continue;
        }
        if (!url.startsWith("http")) {
          url = base + activePath + url;
        }
        String name = text(entry, "name", "title", "slug");
        if (name.isBlank()) {
          name = deriveName(stripSkillFile(url));
        }
        String description = text(entry, "description", "summary");
        if (matchesQuery(query, name, description, url)) {
          refs.add(new SkillRef(name, description, url));
        }
      }
    }
    return refs;
  }

  @Override
  public Skill install(String source) {
    String content = fetchSkillMarkdown(source);
    Frontmatter fm = parseFrontmatter(content);
    String name = fm.name().isBlank() ? deriveName(source) : fm.name();
    if (!SAFE_NAME.matcher(name).matches() || name.contains("..")) {
      throw new IllegalArgumentException("Refusing to install skill with unsafe name: " + name);
    }
    return skillStore.save(new Skill(name, fm.description(), fm.body()));
  }

  @Override
  public Path publish(Skill skill) {
    String name = skill.name() == null ? "" : skill.name().strip();
    if (!SAFE_NAME.matcher(name).matches() || name.contains("..")) {
      throw new IllegalArgumentException("Refusing to publish skill with unsafe name: " + name);
    }
    Path file = Path.of(config.publish().dir()).resolve(name).resolve("SKILL.md");
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, SkillFrontmatter.render(skill), StandardCharsets.UTF_8);
      return file;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to write published skill to " + file, e);
    }
  }

  private String fetchSkillMarkdown(String source) {
    String trimmed = source.strip();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return get(trimmed);
    }
    if (OWNER_REPO.matcher(trimmed).matches()) {
      RuntimeException last = null;
      for (String branch : DEFAULT_BRANCHES) {
        String url = "https://raw.githubusercontent.com/" + trimmed + "/" + branch + "/SKILL.md";
        try {
          return get(url);
        } catch (RuntimeException e) {
          last = e;
        }
      }
      throw new IllegalStateException("Could not fetch SKILL.md for " + trimmed
          + " on any default branch", last);
    }
    throw new IllegalArgumentException(
        "Unrecognized skill source (expected owner/repo or a SKILL.md URL): " + source);
  }

  private String get(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(config.requestTimeout())
          .header("Accept", "application/json, text/plain, */*")
          .GET()
          .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
      }
      return response.body();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Request failed: " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Request interrupted: " + url, e);
    }
  }

  private JsonNode firstArray(JsonNode root, String... fields) {
    if (root.isArray()) {
      return root;
    }
    for (String field : fields) {
      JsonNode candidate = root.get(field);
      if (candidate != null && candidate.isArray()) {
        return candidate;
      }
    }
    return mapper.createArrayNode();
  }

  private String text(JsonNode node, String... fields) {
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && value.isTextual() && !value.asText().isBlank()) {
        return value.asText().strip();
      }
    }
    return "";
  }

  private String deriveName(String source) {
    String slug = source.contains("/") ? source.substring(source.lastIndexOf('/') + 1) : source;
    return slug.replaceAll("\\.md$", "");
  }

  private String tryGet(String url) {
    try {
      return get(url);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private JsonNode readTree(String body, String src) {
    try {
      return mapper.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("Unparseable response from " + src, e);
    }
  }

  private void addUnique(List<SkillRef> target, Set<String> seen, List<SkillRef> found, int cap) {
    for (SkillRef ref : found) {
      if (target.size() >= cap) {
        break;
      }
      if (seen.add(ref.source())) {
        target.add(ref);
      }
    }
  }

  private boolean matchesQuery(String query, String... fields) {
    if (query == null || query.isBlank()) {
      return true;
    }
    String q = query.strip().toLowerCase(Locale.ROOT);
    for (String field : fields) {
      if (field != null && field.toLowerCase(Locale.ROOT).contains(q)) {
        return true;
      }
    }
    return false;
  }

  private String normalizeWellKnown(String host) {
    String s = host.strip();
    if (!s.startsWith("http://") && !s.startsWith("https://")) {
      s = "https://" + s;
    }
    s = s.replaceAll("/\\.well-known/agent-skills/.*$", "");
    s = s.replaceAll("/\\.well-known/skills/?.*$", "");
    return s.replaceAll("/+$", "");
  }

  private String stripSkillFile(String url) {
    String path = url;
    int q = path.indexOf('?');
    if (q >= 0) {
      path = path.substring(0, q);
    }
    if (path.toLowerCase(Locale.ROOT).endsWith("/skill.md")) {
      path = path.substring(0, path.length() - "/SKILL.md".length());
    }
    return path;
  }

  private Frontmatter parseFrontmatter(String content) {
    String normalized = content.stripLeading();
    if (!normalized.startsWith("---")) {
      return new Frontmatter("", "", content);
    }
    int end = normalized.indexOf("\n---", 3);
    if (end < 0) {
      return new Frontmatter("", "", content);
    }
    String header = normalized.substring(normalized.indexOf('\n') + 1, end);
    String body = normalized.substring(normalized.indexOf("\n", end + 1) + 1).stripLeading();
    String name = "";
    String description = "";
    for (String line : header.split("\n")) {
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).strip().toLowerCase();
      String value = unquote(line.substring(colon + 1).strip());
      if (key.equals("name")) {
        name = value;
      } else if (key.equals("description")) {
        description = value;
      }
    }
    return new Frontmatter(name, description, body);
  }

  private String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private record Frontmatter(String name, String description, String body) {
  }
}
