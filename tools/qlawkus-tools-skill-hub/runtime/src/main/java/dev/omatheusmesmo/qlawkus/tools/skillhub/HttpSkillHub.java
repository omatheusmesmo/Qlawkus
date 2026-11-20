package dev.omatheusmesmo.qlawkus.tools.skillhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillFrontmatter;
import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tools.skillhub.ArchiveSkillReader.ArchivedSkill;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.ArchiveSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.FileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GistSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFolderSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubSkillSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Default {@link SkillHub}: talks to the registry over plain HTTP from Java (native-image friendly,
 * no external CLI). Search hits {@code <base-url>/api/search?q=} and, for each configured
 * {@code well-known-hosts} entry, the {@code /.well-known/agent-skills/index.json} index. Install and
 * discover read a source through {@link SkillSourceParser}: a single SKILL.md link, a skill or folder
 * of a GitHub repository, a gist, or a {@code .zip}/{@code .tar.gz} archive. Only SKILL.md text is ever
 * fetched; scripts and assets beside it are not. Publish renders a skill to a SKILL.md under the
 * configured publish directory (no {@code git push} - that stays with the owner on the host).
 *
 * <p>Every request passes {@link OutboundPolicy} first, on the first URL and on each redirect hop, and
 * every body is read under a size cap. This is a {@link DefaultBean} so an alternative backend can
 * override it without touching callers.
 */
@ApplicationScoped
@DefaultBean
public class HttpSkillHub implements SkillHub {

  private static final String GITHUB_RAW = "https://raw.githubusercontent.com";
  private static final String GITHUB_API = "https://api.github.com";
  private static final String ROOT_SKILL = "SKILL.md";
  private static final List<String> DEFAULT_BRANCHES = List.of("main", "master");
  private static final long API_RESPONSE_LIMIT = 8L * 1024 * 1024;
  private static final int MAX_REDIRECTS = 3;
  private static final int MAX_LISTED = 200;

  private final SkillHubConfig config;
  private final SkillStore skillStore;
  private final OutboundPolicy policy;
  private final HttpClient httpClient;
  private final ObjectMapper mapper = new ObjectMapper();
  private final String githubRawBase;
  private final String githubApiBase;

  @Inject
  public HttpSkillHub(SkillHubConfig config, SkillStore skillStore, OutboundPolicy policy) {
    this(config, skillStore, GITHUB_RAW, GITHUB_API, policy);
  }

  HttpSkillHub(SkillHubConfig config, SkillStore skillStore, String githubRawBase, String githubApiBase) {
    this(config, skillStore, githubRawBase, githubApiBase, new OutboundPolicy(false, true));
  }

  HttpSkillHub(SkillHubConfig config, SkillStore skillStore, String githubRawBase, String githubApiBase,
      OutboundPolicy policy) {
    this.config = config;
    this.skillStore = skillStore;
    this.policy = policy;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(config.requestTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
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
    JsonNode hits = firstArray(readTree(downloadText(url, API_RESPONSE_LIMIT), url),
        "results", "skills", "data", "items");
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
    if (!source.matches("^[\\w.-]+/[\\w.-]+$")) {
      return Optional.empty();
    }
    if (skillId.isBlank()) {
      return Optional.of(source);
    }
    return SkillMarkdown.isSafeName(skillId)
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
   * Installs the one skill {@code source} names. A source that holds several skills is refused with the
   * count, so the caller can list them and install a selection instead. Every failure is logged at WARN
   * with the source and rethrown, so the tool framework reports it to the model as an error result and
   * {@code MeteredToolExecutor} counts it as {@code outcome=error}.
   */
  @Override
  public Skill install(String source) {
    try {
      SkillSource parsed = SkillSourceParser.parse(source);
      String content = singleContent(parsed, source.strip());
      Skill saved = skillStore.save(SkillMarkdown.parse(content, deriveName(parsed, source.strip())));
      skillStore.recordOrigin(saved.name(), new SkillOrigin(source.strip(), null, System.currentTimeMillis()));
      return saved;
    } catch (RuntimeException e) {
      Log.warnf("Skill install failed for source '%s': %s", source, e.getMessage());
      throw e;
    }
  }

  @Override
  public List<SkillCandidate> discover(String source) {
    try {
      SkillSource parsed = SkillSourceParser.parse(source);
      String trimmed = source.strip();
      return switch (parsed) {
        case FileSource file -> List.of(new SkillCandidate(deriveName(file.url()), trimmed, file.url(), null));
        case GitHubFileSource file -> List.of(new SkillCandidate(
            folderName(file.path(), repoName(file.repo())), trimmed,
            rawUrl(file.repo(), file.ref(), file.path()), null));
        case GitHubSkillSource named -> List.of(new SkillCandidate(named.skill(), trimmed, null,
            fetchNamedSkill(named.repo(), named.skill())));
        case GitHubFolderSource folder -> listRepositoryFolder(folder, trimmed);
        case ArchiveSource archive -> archiveCandidates(archive.url(), trimmed);
        case GistSource gist -> List.of(gistCandidate(gist.id(), trimmed));
        case NameSource name -> throw notALocation(name.name());
      };
    } catch (RuntimeException e) {
      Log.warnf("Skill discovery failed for source '%s': %s", source, e.getMessage());
      throw e;
    }
  }

  @Override
  public Skill installCandidate(SkillCandidate candidate) {
    try {
      String content = candidate.content() != null
          ? candidate.content()
          : downloadText(candidate.location(), config.maxSkillBytes());
      Skill saved = skillStore.save(SkillMarkdown.parse(content, candidate.name()));
      skillStore.recordOrigin(saved.name(),
          new SkillOrigin(candidate.source(), candidate.name(), System.currentTimeMillis()));
      return saved;
    } catch (RuntimeException e) {
      Log.warnf("Skill install failed for candidate '%s' from '%s': %s", candidate.name(), candidate.source(),
          e.getMessage());
      throw e;
    }
  }

  @Override
  public Path publish(Skill skill) {
    String name = skill.name() == null ? "" : skill.name().strip();
    if (!SkillMarkdown.isSafeName(name)) {
      throw new IllegalArgumentException("Refusing to publish skill with unsafe name: " + name);
    }
    Path file = Path.of(config.publish().dir()).resolve(name).resolve("SKILL.md");
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, SkillFrontmatter.render(skill), StandardCharsets.UTF_8);
      return file;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write published skill to " + file, e);
    }
  }

  private String singleContent(SkillSource parsed, String source) {
    return switch (parsed) {
      case FileSource file -> downloadText(file.url(), config.maxSkillBytes());
      case GitHubFileSource file -> downloadText(rawUrl(file.repo(), file.ref(), file.path()),
          config.maxSkillBytes());
      case GitHubSkillSource named -> fetchNamedSkill(named.repo(), named.skill());
      case GitHubFolderSource folder -> fetchFolderSkill(folder, source);
      case ArchiveSource archive -> onlyCandidate(archiveCandidates(archive.url(), source), source)
          .content();
      case GistSource gist -> downloadText(gistCandidate(gist.id(), source).location(), config.maxSkillBytes());
      case NameSource name -> throw notALocation(name.name());
    };
  }

  private IllegalArgumentException notALocation(String name) {
    return new IllegalArgumentException("'" + name + "' is a skill name, not a location. Search the registry "
        + "with searchSkillHub (or installSkill with the name, which searches for you) and install the "
        + "source it returns.");
  }

  private SkillCandidate onlyCandidate(List<SkillCandidate> found, String source) {
    if (found.size() != 1) {
      throw new IllegalStateException("'" + source + "' holds " + found.size() + " skills, so it cannot be "
          + "installed in one go. List them with listSkillsInSource and install the ones you want with "
          + "installSkillsFromSource.");
    }
    return found.get(0);
  }

  /**
   * A repository folder with an explicit ref is one file. Without a ref the default branches are tried,
   * and the root of the repository keeps its original behaviour: a root SKILL.md, then the named skill.
   */
  private String fetchFolderSkill(GitHubFolderSource folder, String source) {
    if (folder.folder().isEmpty()) {
      return folder.ref() == null
          ? fetchRootSkill(folder.repo())
          : downloadText(rawUrl(folder.repo(), folder.ref(), ROOT_SKILL), config.maxSkillBytes());
    }
    String path = folder.folder() + "/" + ROOT_SKILL;
    List<String> branches = folder.ref() == null ? DEFAULT_BRANCHES : List.of(folder.ref());
    RuntimeException last = null;
    for (String branch : branches) {
      try {
        return downloadText(rawUrl(folder.repo(), branch, path), config.maxSkillBytes());
      } catch (RuntimeException e) {
        last = e;
      }
    }
    throw new IllegalStateException("Could not fetch " + path + " from " + folder.repo() + " on "
        + String.join(" or ", branches) + " (source '" + source + "'). Choose a skill folder that holds a "
        + "SKILL.md, or list what the folder holds with listSkillsInSource.", last);
  }

  private List<SkillCandidate> listRepositoryFolder(GitHubFolderSource folder, String source) {
    String prefix = folder.folder().isEmpty() ? "" : folder.folder() + "/";
    List<String> branches = folder.ref() == null ? DEFAULT_BRANCHES : List.of(folder.ref());
    RuntimeException last = null;
    for (String branch : branches) {
      JsonNode tree;
      try {
        String url = githubApiBase + "/repos/" + folder.repo() + "/git/trees/" + branch + "?recursive=1";
        tree = readTree(downloadText(url, API_RESPONSE_LIMIT), url);
      } catch (RuntimeException e) {
        last = e;
        continue;
      }
      if (tree.path("truncated").asBoolean(false)) {
        throw new IllegalStateException("GitHub truncated the file tree of " + folder.repo()
            + "; point at a deeper folder with a tree link (source '" + source + "').");
      }
      List<SkillCandidate> found = new ArrayList<>();
      for (JsonNode entry : tree.path("tree")) {
        String path = entry.path("path").asText("");
        if (!"blob".equals(entry.path("type").asText()) || !path.startsWith(prefix)
            || !(path.equals(ROOT_SKILL) || path.endsWith("/" + ROOT_SKILL))) {
          continue;
        }
        String name = path.equals(ROOT_SKILL) ? repoName(folder.repo()) : folderName(path, repoName(folder.repo()));
        found.add(new SkillCandidate(name, source, rawUrl(folder.repo(), branch, path), null));
        if (found.size() > MAX_LISTED) {
          throw new IllegalStateException("'" + source + "' holds more than " + MAX_LISTED
              + " skills; point at a deeper folder with a tree link.");
        }
      }
      if (found.isEmpty()) {
        throw new IllegalStateException("No SKILL.md under '" + (folder.folder().isEmpty() ? "the repository root"
            : folder.folder()) + "' of " + folder.repo() + " on " + branch + " (source '" + source + "').");
      }
      return found;
    }
    throw new IllegalStateException("Could not read the file tree of " + folder.repo() + " on "
        + String.join(" or ", branches) + " (source '" + source + "').", last);
  }

  private List<SkillCandidate> archiveCandidates(String url, String source) {
    byte[] archive = download(url, config.archive().maxBytes());
    List<ArchivedSkill> skills = ArchiveSkillReader.read(archive,
        new ArchiveSkillReader.Limits(config.archive().maxEntries(), config.archive().maxExpandedBytes(),
            config.maxSkillBytes()));
    if (skills.isEmpty()) {
      throw new IllegalStateException("The archive at '" + source + "' holds no SKILL.md file.");
    }
    String archiveName = deriveName(url);
    List<SkillCandidate> candidates = new ArrayList<>();
    for (ArchivedSkill skill : skills) {
      candidates.add(new SkillCandidate(skill.folder().isEmpty() ? archiveName : folderName(skill.folder(), archiveName),
          source, null, skill.content()));
    }
    return candidates;
  }

  private SkillCandidate gistCandidate(String id, String source) {
    String url = githubApiBase + "/gists/" + id;
    JsonNode gist = readTree(downloadText(url, API_RESPONSE_LIMIT), url);
    JsonNode file = gist.path("files").path(ROOT_SKILL);
    String raw = text(file, "raw_url");
    if (raw.isBlank()) {
      throw new IllegalStateException("The gist " + id + " holds no " + ROOT_SKILL + " file (source '" + source
          + "'). Gists are installed through their SKILL.md file.");
    }
    return new SkillCandidate(id, source, raw, null);
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
  Exchange exchange(URI uri, long limit) {
    try {
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(config.requestTimeout())
          .header("Accept", "application/json, text/plain, */*")
          .GET()
          .build();
      HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      int status = response.statusCode();
      try (InputStream body = response.body()) {
        if (status == 429 || status >= 500) {
          throw new TransientHttpException(status, "from " + uri);
        }
        if (status >= 300 && status < 400) {
          String location = response.headers().firstValue("Location").orElse(null);
          if (location == null) {
            throw new IllegalStateException("HTTP " + status + " without a Location header from " + uri);
          }
          return new Exchange(location, null);
        }
        if (status / 100 != 2) {
          throw new IllegalStateException("HTTP " + status + " from " + uri);
        }
        return new Exchange(null, readBounded(body, limit, uri));
      }
    } catch (IOException e) {
      throw new TransientHttpException("Request failed: " + uri, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Request interrupted: " + uri, e);
    }
  }

  /** One response: a redirect target (no body) or the bounded body of a 2xx. */
  record Exchange(String redirectTo, byte[] body) {
  }

  /**
   * Fetches {@code url}, checking the policy before every hop and following at most
   * {@link #MAX_REDIRECTS} redirects, each resolved against the current URL and checked again.
   */
  private byte[] download(String url, long limit) {
    URI uri = URI.create(url.strip());
    for (int hop = 0; ; hop++) {
      policy.check(uri);
      Exchange exchange = exchange(uri, limit);
      if (exchange.redirectTo() == null) {
        return exchange.body();
      }
      if (hop >= MAX_REDIRECTS) {
        throw new IllegalStateException("Too many redirects (more than " + MAX_REDIRECTS + ") from " + url);
      }
      uri = uri.resolve(exchange.redirectTo());
    }
  }

  private String downloadText(String url, long limit) {
    return new String(download(url, limit), StandardCharsets.UTF_8);
  }

  private static byte[] readBounded(InputStream in, long limit, URI uri) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    long total = 0;
    int read;
    while ((read = in.read(chunk)) >= 0) {
      total += read;
      if (total > limit) {
        throw new IllegalArgumentException("'" + uri + "' is larger than the " + limit + "-byte limit.");
      }
      out.write(chunk, 0, read);
    }
    return out.toByteArray();
  }

  private String fetchRootSkill(String repo) {
    RuntimeException last = null;
    for (String branch : DEFAULT_BRANCHES) {
      try {
        return downloadText(rawUrl(repo, branch, ROOT_SKILL), config.maxSkillBytes());
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
          return downloadText(rawUrl(repo, branch, path), config.maxSkillBytes());
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
        tree = readTree(downloadText(url, API_RESPONSE_LIMIT), url);
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
        return downloadText(rawUrl(repo, branch, matches.get(0)), config.maxSkillBytes());
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
        String content = downloadText(rawUrl(repo, branch, ROOT_SKILL), config.maxSkillBytes());
        if (skill.equalsIgnoreCase(SkillMarkdown.parse(content, skill).name())) {
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

  private String deriveName(SkillSource parsed, String source) {
    return switch (parsed) {
      case FileSource file -> deriveName(file.url());
      case GitHubFileSource file -> folderName(file.path(), repoName(file.repo()));
      case GitHubSkillSource named -> named.skill();
      case GitHubFolderSource folder -> folder.folder().isEmpty()
          ? repoName(folder.repo())
          : folderName(folder.folder(), repoName(folder.repo()));
      case ArchiveSource archive -> deriveName(archive.url());
      case GistSource gist -> gist.id();
      case NameSource name -> name.name();
    };
  }

  /** The skill folder a SKILL.md path sits in, or the given fallback for a file at the root. */
  private static String folderName(String path, String fallback) {
    String trimmed = path.endsWith("/" + ROOT_SKILL) ? path.substring(0, path.length() - ROOT_SKILL.length() - 1)
        : path.equals(ROOT_SKILL) ? "" : path;
    int slash = trimmed.lastIndexOf('/');
    String name = slash < 0 ? trimmed : trimmed.substring(slash + 1);
    return name.isBlank() ? fallback : name;
  }

  private static String repoName(String repo) {
    return repo.substring(repo.indexOf('/') + 1);
  }

  private String deriveName(String source) {
    String path = source;
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    String slug = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    if (slug.equalsIgnoreCase(ROOT_SKILL)) {
      String withoutFile = path.substring(0, path.length() - ROOT_SKILL.length() - 1);
      return folderName(withoutFile, slug);
    }
    return slug.replaceAll("\\.md$", "").replaceAll("\\.(zip|tar\\.gz|tgz)$", "");
  }

  private String tryGet(String url) {
    try {
      return downloadText(url, API_RESPONSE_LIMIT);
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
}
