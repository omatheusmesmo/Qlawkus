package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the markdown owner-profile backend: load returns a non-null empty profile before any
 * file exists (matching the seeded-but-empty pgvector row), and saves round-trip through
 * {@code owner.md}.
 */
class MarkdownUserProfileStoreTest {

  @TempDir
  Path tempDir;

  private MarkdownUserProfileStore store() {
    return new MarkdownUserProfileStore(tempDir.toString());
  }

  @Test
  void loadBeforeAnyFileReturnsEmptyProfile() {
    UserProfile profile = store().load();
    assertNotNull(profile);
    assertNull(profile.name);
    assertNull(profile.profile);
  }

  @Test
  void saveThenLoadRoundTrips() {
    MarkdownUserProfileStore store = store();
    UserProfile profile = store.load();
    profile.rename("Matheus");
    profile.rewriteProfile("Works with Java and Quarkus.\nPrefers constructor injection.");
    store.save(profile);

    UserProfile reloaded = store.load();
    assertEquals("Matheus", reloaded.name);
    assertTrue(reloaded.profile.contains("constructor injection"));
  }

  @Test
  void renderedContextBlockReflectsSavedProfile() {
    MarkdownUserProfileStore store = store();
    UserProfile profile = store.load();
    profile.rename("Matheus");
    profile.rewriteProfile("Quarkus engineer.");
    store.save(profile);

    String block = store.load().toContextBlock();
    assertTrue(block.contains("Matheus"));
    assertTrue(block.contains("Quarkus engineer."));
  }
}
