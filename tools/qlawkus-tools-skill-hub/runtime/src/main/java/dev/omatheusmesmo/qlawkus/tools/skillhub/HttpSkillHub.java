package dev.omatheusmesmo.qlawkus.tools.skillhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillFrontmatter;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link SkillHub}: talks to the registry over plain HTTP from Java (native-image friendly,
 * no external CLI). Search hits {@code <base-url>/api/search?q=} and, for each configured
 * {@code well-known-hosts} entry, the {@code /.well-known/agent-skills/index.json} index. Install
 * resolves an {@code owner/repo} slug, an {@code owner/repo/skill} reference (a skill in a subfolder),
 * or a direct SKILL.md URL to the raw SKILL.md, parses its frontmatter, and saves it through
 * {@link SkillStore} into the owned root. Publish renders a skill to a SKILL.md under the configured
 * publish directory (no {@code git push} - that stays with the owner on the host).
 *
 * <p>This is a {@link DefaultBean} so an alternative backend can override it without touching
 * callers.
 */
@ApplicationScoped
@DefaultBean
public class HttpSkillHub implements SkillHub {

  private static final String GITHUB_RAW = "https://raw.githubusercontent.com";
  private static final String GITHUB_API = "https://api.github.com";
  private static final Pattern OWNER_REPO = Pattern.compile("^[\\w.-]+/[\\w.-]+$");
  private static final Pattern GITHUB_SOURCE =
      Pattern.compile("^([\\w.-]+/[\\w.-]+)(?:/([a-zA-Z0-9][a-zA-Z0-9._-]*))?$");
  private static final String ROOT_SKILL = "SKILL.md";
  private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  private static final List<String> DEFAULT_BRANCHES = List.of("main", "master");

  private final SkillHubConfig config;
  private final SkillStore skillStore;
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String githubRawBase;
  private final String githubApiBase;

  @Inject
  public HttpSkillHub(SkillHubConfig config, SkillStore skillStore) {
    this(config, skillStore, GITHUB_RAW, GITHUB_API);
  }

  HttpSkillHub(SkillHubConfig config, SkillStore skillStore, String githubRawBase, String githubApiBase) {
    this.config = config;
    this.skillStore = skillStore;
    this.httpClient = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    this.githubRawBase = githubRawBase;
    this.githubApiBase = githubApiBase;
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
      Optional<String> reference = installReference(source, text(node, "skillId"));
      if (!name.isBlank() && reference.isPresent()) {
        refs.add(new SkillRef(name, text(node, "description", "summary"), reference.get()));
      } else if (!name.isBlank()) {
        Log.debugf("Skipped skill hub hit '%s' from '%s': no installable source", name, source);
      }
    }
    return refs;
  }

  /**
   * A skills.sh hit names its repository and its skill folder separately; the reference keeps both
   * so install can find a skill nested below the repository root. A bare host has no install path.
   */
  private Optional<String> installReference(String source, String skillId) {
    if (source.startsWith("http://") || source.startsWith("https://")) {
      return Optional.of(source);
    }
    if (!OWNER_REPO.matcher(source).matches()) {
      return Optional.empty();
    }
    if (skillId.isBlank()) {
      return Optional.of(source);
    }
    return SAFE_NAME.matcher(skillId).matches()
        ? Optional.of(source + "/" + skillId)
        : Optional.empty();
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

  /**
   * Every failure is logged at WARN with the source and rethrown, so the tool framework reports it to
   * the model as an error result and {@code MeteredToolExecutor} counts it as {@code outcome=error}.
   */
  @Override
  public Skill install(String source) {
    try {
      String content = fetchSkillMarkdown(source);
      Frontmatter fm = parseFrontmatter(content);
      String name = fm.name().isBlank() ? deriveName(source) : fm.name();
      if (!SAFE_NAME.matcher(name).matches() || name.contains("..")) {
        throw new IllegalArgumentException("Refusing to install skill with unsafe name: " + name);
      }
      return skillStore.save(new Skill(name, fm.description(), fm.body()));
    } catch (RuntimeException e) {
      Log.warnf("Skill install failed for source '%s': %s", source, e.getMessage());
      throw e;
    }
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
    Matcher reference = GITHUB_SOURCE.matcher(trimmed);
    if (!reference.matches()) {
      throw new IllegalArgumentException("Unrecognized skill source "
          + "(expected owner/repo, owner/repo/skill or a SKILL.md URL): " + source);
    }
    String repo = reference.group(1);
    String skill = reference.group(2);
    return skill == null ? fetchRootSkill(repo) : fetchNamedSkill(repo, skill);
  }

  private String fetchRootSkill(String repo) {
    RuntimeException last = null;
    for (String branch : DEFAULT_BRANCHES) {
      try {
        return get(rawUrl(repo, branch, ROOT_SKILL));
      } catch (RuntimeException e) {
        last = e;
      }
    }
    throw new IllegalStateException("Could not fetch SKILL.md for " + repo
        + " on any default branch", last);
  }

  /**
   * A skill named by the hit lives in a folder, so the folder paths are tried first, then the
   * repository tree (one API call per branch) for a nested folder such as
   * {@code skills/engineering/<skill>/SKILL.md}. The root SKILL.md is the last resort and is used only
   * when it declares the named skill, since in a multi-skill repository it may belong to another one.
   */
  private String fetchNamedSkill(String repo, String skill) {
    List<String> direct = List.of("skills/" + skill + "/" + ROOT_SKILL, skill + "/" + ROOT_SKILL);
    RuntimeException last = null;
    for (String branch : DEFAULT_BRANCHES) {
      for (String path : direct) {
        try {
          return get(rawUrl(repo, branch, path));
        } catch (RuntimeException e) {
          last = e;
        }
      }
    }
    String truncation = "";
    for (String branch : DEFAULT_BRANCHES) {
      JsonNode tree;
      try {
        String url = githubApiBase + "/repos/" + repo + "/git/trees/" + branch + "?recursive=1";
        tree = readTree(get(url), url);
      } catch (RuntimeException e) {
        last = e;
        continue;
      }
      List<String> matches = skillPathsInTree(tree, skill);
      if (matches.size() > 1) {
        throw new IllegalStateException("Several SKILL.md files named " + skill + " in " + repo
            + ": " + String.join(", ", matches));
      }
      if (!matches.isEmpty()) {
        return get(rawUrl(repo, branch, matches.get(0)));
      }
      truncation = tree.path("truncated").asBoolean(false) ? " (GitHub truncated the tree)" : "";
      break;
    }
    String rootSkill = rootSkillNamed(repo, skill);
    if (rootSkill != null) {
      return rootSkill;
    }
    throw new IllegalStateException("Could not find a SKILL.md for " + repo + "/" + skill
        + ". Tried " + String.join(", ", direct) + " on main and master, then the repository tree"
        + truncation + ". Choose another skill from searchSkillHub.", last);
  }

  private String rootSkillNamed(String repo, String skill) {
    for (String branch : DEFAULT_BRANCHES) {
      try {
        String content = get(rawUrl(repo, branch, ROOT_SKILL));
        if (skill.equalsIgnoreCase(parseFrontmatter(content).name())) {
          return content;
        }
        return null;
      } catch (RuntimeException e) {
        continue;
      }
    }
    return null;
  }

  private List<String> skillPathsInTree(JsonNode tree, String skill) {
    String suffix = "/" + skill + "/" + ROOT_SKILL;
    List<String> matches = new ArrayList<>();
    for (JsonNode entry : tree.path("tree")) {
      String path = entry.path("path").asText("");
      if ("blob".equals(entry.path("type").asText()) && ("/" + path).endsWith(suffix)) {
        matches.add(path);
      }
    }
    return matches;
  }

  private String rawUrl(String repo, String branch, String path) {
    return githubRawBase + "/" + repo + "/" + branch + "/" + path;
  }

  /**
   * {@code @Retry} covers one HTTP call, not one {@link #search}/{@link #install}/{@link #publish}
   * operation. Only {@link TransientHttpException} (429, 5xx, or a network-level failure reaching
   * the registry at all) is retried; every other status - 404 for an unknown SKILL.md, for instance
   * - propagates immediately since retrying it cannot succeed.
   */
  @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
      jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
      retryOn = TransientHttpException.class)
  @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
  String get(String url) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(config.requestTimeout())
          .header("Accept", "application/json, text/plain, */*")
          .GET()
          .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status / 100 != 2) {
        if (status == 429 || status >= 500) {
          throw new TransientHttpException(status, "from " + url);
        }
        throw new IllegalStateException("HTTP " + status + " from " + url);
      }
      return response.body();
    } catch (java.io.IOException e) {
      throw new TransientHttpException("Request failed: " + url, e);
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
