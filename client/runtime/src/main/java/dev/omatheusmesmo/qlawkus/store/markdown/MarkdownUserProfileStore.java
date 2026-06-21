package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Markdown-backed {@link UserProfileStore}, active when {@code qlawkus.cognition.backend=markdown}.
 * The owner is a single {@code <root>/owner.md} file: YAML frontmatter ({@code name}) over the
 * profile markdown body. {@link #load} always returns a non-null profile (empty when no file yet),
 * matching the seeded-but-empty {@code user_profile} row the pgvector backend starts with, so the
 * system prompt renders the "record a profile" nudge identically.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "markdown")
public class MarkdownUserProfileStore implements UserProfileStore {

  private static final String FILE = "owner.md";
  private static final String DELIMITER = "---";

  private final Path root;

  @Inject
  public MarkdownUserProfileStore(AgentConfig config) {
    this(config.state().root());
  }

  public MarkdownUserProfileStore(String root) {
    this.root = Path.of(root);
  }

  @Override
  public UserProfile load() {
    UserProfile profile = new UserProfile();
    profile.id = 1L;
    Path file = root.resolve(FILE);
    if (!Files.isRegularFile(file)) {
      return profile;
    }
    String text;
    try {
      text = Files.readString(file, StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + file, e);
    }
    String body = text;
    if (text.startsWith(DELIMITER)) {
      int end = text.indexOf("\n" + DELIMITER, DELIMITER.length());
      if (end >= 0) {
        String header = text.substring(DELIMITER.length(), end).strip();
        body = text.substring(end + ("\n" + DELIMITER).length()).strip();
        for (String line : header.split("\n")) {
          int colon = line.indexOf(':');
          if (colon > 0 && line.substring(0, colon).strip().equals("name")) {
            String value = line.substring(colon + 1).strip();
            profile.name = value.isBlank() ? null : value;
          }
        }
      }
    }
    profile.profile = body.isBlank() ? null : body;
    return profile;
  }

  @Override
  public void save(UserProfile profile) {
    String content = DELIMITER + "\n"
        + "name: " + (profile.name == null ? "" : profile.name) + "\n"
        + DELIMITER + "\n\n"
        + (profile.profile == null ? "" : profile.profile.strip()) + "\n";
    try {
      Files.createDirectories(root);
      Files.writeString(root.resolve(FILE), content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + root.resolve(FILE), e);
    }
  }
}
