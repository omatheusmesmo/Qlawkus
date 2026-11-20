package dev.omatheusmesmo.qlawkus.tools.skillhub;

/**
 * A skill source the owner or the registry named, after normalization. The parser turns every link
 * shape into one of these; {@link HttpSkillHub} then maps it to the URLs it fetches, so the
 * configured GitHub hosts stay in one place.
 */
public sealed interface SkillSource {

  /** A single https URL that serves a SKILL.md text, fetched as-is. */
  record FileSource(String url) implements SkillSource {
  }

  /** A skill folder inside a GitHub repository, named as {@code owner/repo/skill}. */
  record GitHubSkillSource(String repo, String skill) implements SkillSource {
  }

  /**
   * A folder of a GitHub repository (empty {@code folder} is the repository root). A null {@code ref}
   * means the default branches are tried in turn.
   */
  record GitHubFolderSource(String repo, String ref, String folder) implements SkillSource {
  }

  /** One file of a GitHub repository at a ref, from a {@code blob} link. */
  record GitHubFileSource(String repo, String ref, String path) implements SkillSource {
  }

  /** A {@code .zip} or {@code .tar.gz} archive holding one or more skill folders. */
  record ArchiveSource(String url) implements SkillSource {
  }

  /** A GitHub gist holding a SKILL.md file. */
  record GistSource(String id) implements SkillSource {
  }

  /** A bare skill name with no location: resolved through the registry search. */
  record NameSource(String name) implements SkillSource {
  }
}
