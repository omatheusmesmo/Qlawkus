package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.ArchiveSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.FileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GistSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFileSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubFolderSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.GitHubSkillSource;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillSource.NameSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns whatever the owner pasted (an {@code owner/repo} slug, a skill name, or a link copied from a
 * browser) into a {@link SkillSource}. Only https is accepted, and a rejected source names the forms
 * that would work, because the model reads that message to decide what to ask the owner.
 *
 * <p>Known shapes: GitHub {@code blob}, {@code tree}, {@code raw} and repository links; skills.sh skill
 * and repository pages; GitLab ({@code /-/blob}, {@code /-/tree}) and Bitbucket ({@code /src}) file and
 * folder links; gist links; {@code .zip} and {@code .tar.gz} archive links. Any other https URL is taken
 * to be a SKILL.md, as before.
 */
public final class SkillSourceParser {

  private static final Pattern GITHUB_OWNER = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");
  private static final Pattern REPO = Pattern.compile("^[\\w.-]+$");
  private static final Pattern SKILL_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
  private static final Pattern PLAIN_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");
  private static final Pattern PATH_SEGMENT = Pattern.compile("^[\\w.+@-]+$");
  private static final Pattern GIST_ID = Pattern.compile("^[0-9a-zA-Z]{6,}$");
  private static final String SKILL_FILE = "SKILL.md";
  private static final String HINT = "Accepted forms: a skill name to search for, an owner/repo slug "
      + "(optionally /skill), or an https link to a SKILL.md, GitHub, GitLab or Bitbucket file or folder, "
      + "a gist, or a .zip or .tar.gz archive";

  private SkillSourceParser() {
  }

  /**
   * Parses a source string.
   *
   * @throws IllegalArgumentException when the source is empty, carries credentials, or has a shape no
   *     source form covers; the message says what was refused and what is accepted. A plain http link is
   *     parsed like an https one: {@link OutboundPolicy} refuses it when it is fetched
   */
  public static SkillSource parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("No skill source given. " + HINT + ".");
    }
    String source = raw.strip();
    String lower = source.toLowerCase(Locale.ROOT);
    if (lower.startsWith("https://") || lower.startsWith("http://")) {
      return parseUrl(source);
    }
    if (source.contains("://")) {
      throw new IllegalArgumentException("Refused '" + source + "': only https:// links are supported. "
          + HINT + ".");
    }
    return parseSlug(source);
  }

  private static SkillSource parseSlug(String source) {
    String[] parts = source.split("/", -1);
    if (parts.length == 1 && PLAIN_NAME.matcher(source).matches()) {
      return new NameSource(source);
    }
    if (parts.length < 2 || !GITHUB_OWNER.matcher(parts[0]).matches() || !isRepo(parts[1])) {
      throw unrecognized(source);
    }
    String repo = parts[0] + "/" + parts[1];
    if (parts.length == 2) {
      return new GitHubFolderSource(repo, null, "");
    }
    if (parts.length == 3 && SKILL_NAME.matcher(parts[2]).matches() && !parts[2].contains("..")) {
      return new GitHubSkillSource(repo, parts[2]);
    }
    return new GitHubFolderSource(repo, null, safePath(String.join("/", Arrays.copyOfRange(parts, 2, parts.length)), source));
  }

  private static SkillSource parseUrl(String source) {
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Refused '" + source + "': not a valid URL. " + HINT + ".", e);
    }
    String host = uri.getHost();
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Refused a URL with credentials in it (host "
          + (host == null ? "unknown" : host) + "). Paste the plain link without user:password.");
    }
    if (host == null) {
      throw unrecognized(source);
    }
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (normalizedHost.startsWith("www.")) {
      normalizedHost = normalizedHost.substring("www.".length());
    }
    List<String> segments = segments(uri.getPath());
    String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
    return switch (normalizedHost) {
      case "github.com" -> parseGitHub(segments, source);
      case "raw.githubusercontent.com" -> new FileSource(source);
      case "gist.github.com" -> parseGist(segments, source);
      case "gitlab.com" -> parseGitLab(segments, source);
      case "bitbucket.org" -> parseBitbucket(segments, source);
      case "skills.sh" -> parseSkillsSh(segments, source);
      default -> isArchivePath(path) ? new ArchiveSource(source) : new FileSource(source);
    };
  }

  private static SkillSource parseGitHub(List<String> segments, String source) {
    if (segments.size() < 2 || !isRepo(segments.get(1).replaceAll("\\.git$", ""))) {
      throw unrecognized(source);
    }
    String repo = segments.get(0) + "/" + segments.get(1).replaceAll("\\.git$", "");
    if (segments.size() == 2) {
      return new GitHubFolderSource(repo, null, "");
    }
    if (segments.size() >= 5 && "blob".equals(segments.get(2))) {
      String path = safePath(String.join("/", segments.subList(4, segments.size())), source);
      return new GitHubFileSource(repo, segments.get(3), path);
    }
    if (segments.size() >= 4 && "tree".equals(segments.get(2))) {
      String folder = segments.size() == 4
          ? ""
          : safePath(String.join("/", segments.subList(4, segments.size())), source);
      return new GitHubFolderSource(repo, segments.get(3), folder);
    }
    throw unrecognized(source);
  }

  private static SkillSource parseGist(List<String> segments, String source) {
    if (segments.isEmpty() || segments.size() > 2) {
      throw unrecognized(source);
    }
    String id = segments.get(segments.size() - 1);
    if (!GIST_ID.matcher(id).matches()) {
      throw unrecognized(source);
    }
    return new GistSource(id);
  }

  private static SkillSource parseGitLab(List<String> segments, String source) {
    int marker = segments.indexOf("-");
    if (marker < 2 || marker + 2 >= segments.size()) {
      throw unrecognized(source);
    }
    String base = "https://gitlab.com/" + String.join("/", segments.subList(0, marker));
    String kind = segments.get(marker + 1);
    String ref = segments.get(marker + 2);
    List<String> rest = segments.subList(marker + 3, segments.size());
    String file = rest.isEmpty() ? "" : safePath(String.join("/", rest), source);
    return switch (kind) {
      case "blob", "raw" -> {
        if (file.isEmpty()) {
          throw unrecognized(source);
        }
        yield new FileSource(base + "/-/raw/" + ref + "/" + file);
      }
      case "tree" -> new FileSource(base + "/-/raw/" + ref + "/" + (file.isEmpty() ? "" : file + "/")
          + SKILL_FILE);
      default -> throw unrecognized(source);
    };
  }

  private static SkillSource parseBitbucket(List<String> segments, String source) {
    if (segments.size() < 5 || !"src".equals(segments.get(2))) {
      throw unrecognized(source);
    }
    String base = "https://bitbucket.org/" + segments.get(0) + "/" + segments.get(1);
    String ref = segments.get(3);
    String path = safePath(String.join("/", segments.subList(4, segments.size())), source);
    String file = path.toLowerCase(Locale.ROOT).endsWith(".md") ? path : path + "/" + SKILL_FILE;
    return new FileSource(base + "/raw/" + ref + "/" + file);
  }

  private static SkillSource parseSkillsSh(List<String> segments, String source) {
    if (segments.size() == 2 && isRepo(segments.get(1))) {
      return new GitHubFolderSource(segments.get(0) + "/" + segments.get(1), null, "");
    }
    if (segments.size() == 3 && isRepo(segments.get(1)) && SKILL_NAME.matcher(segments.get(2)).matches()
        && !segments.get(2).contains("..")) {
      return new GitHubSkillSource(segments.get(0) + "/" + segments.get(1), segments.get(2));
    }
    throw unrecognized(source);
  }

  private static boolean isArchivePath(String lowerPath) {
    return lowerPath.endsWith(".zip") || lowerPath.endsWith(".tar.gz") || lowerPath.endsWith(".tgz");
  }

  private static List<String> segments(String path) {
    List<String> segments = new ArrayList<>();
    if (path == null) {
      return segments;
    }
    for (String segment : path.split("/")) {
      if (!segment.isEmpty()) {
        segments.add(segment);
      }
    }
    return segments;
  }

  private static boolean isRepo(String value) {
    return REPO.matcher(value).matches() && !".".equals(value) && !"..".equals(value);
  }

  private static String safePath(String path, String source) {
    for (String segment : path.split("/")) {
      if (!PATH_SEGMENT.matcher(segment).matches() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Refused '" + source + "': the path segment '" + segment
            + "' is not a plain folder or file name.");
      }
    }
    return path;
  }

  private static IllegalArgumentException unrecognized(String source) {
    return new IllegalArgumentException("Unrecognized skill source '" + source + "'. " + HINT + ".");
  }
}
