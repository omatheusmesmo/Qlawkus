package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the markdown persona backend: first load seeds the bundled default persona, and
 * saves round-trip through {@code soul.md} (frontmatter + sentinel-split body), no database.
 */
class MarkdownSoulStoreTest {

  @TempDir
  Path tempDir;

  private MarkdownSoulStore store() {
    return new MarkdownSoulStore(tempDir.toString());
  }

  @Test
  void firstLoadSeedsDefaultPersona() {
    Soul soul = store().load();

    assertNotNull(soul);
    assertEquals("Qlawkus", soul.name);
    assertEquals(Mood.FOCUSED, soul.mood);
    assertTrue(soul.coreIdentity.contains("Who I Am"));
    assertTrue(soul.currentState.contains("Awaiting first interaction"));
    assertTrue(Files.isRegularFile(tempDir.resolve("soul.md")), "default should be seeded to disk");
  }

  @Test
  void saveThenLoadRoundTrips() {
    MarkdownSoulStore store = store();
    Soul soul = store.load();
    soul.rename("Aria");
    soul.shiftMood(Mood.CURIOUS);
    soul.shiftState("Reviewing pull requests.");
    soul.rewriteIdentity("## Who I Am\n\nI am Aria.\n\n## How I Work\n\nCarefully.");
    store.save(soul);

    Soul reloaded = store.load();
    assertEquals("Aria", reloaded.name);
    assertEquals(Mood.CURIOUS, reloaded.mood);
    assertEquals("Reviewing pull requests.", reloaded.currentState);
    assertTrue(reloaded.coreIdentity.contains("I am Aria."));
    assertTrue(reloaded.coreIdentity.contains("## How I Work"),
        "core identity with its own headers must survive the sentinel split");
  }

  @Test
  void toSystemMessageComposesFromLoadedPersona() {
    String message = store().load().toSystemMessage();
    assertTrue(message.startsWith("# Qlawkus"));
    assertTrue(message.contains("## Current State"));
    assertTrue(message.contains("## Current Mood"));
  }
}
