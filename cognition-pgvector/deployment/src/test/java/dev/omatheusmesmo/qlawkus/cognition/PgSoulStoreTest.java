package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.SoulStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test for the pgvector persona backend ({@code PgSoulStore} via {@code SoulEntity}),
 * mirroring {@code MarkdownSoulStoreTest} on the database side: persona mutations persist to the
 * {@code soul} table and reload intact. Runs against Dev Services Postgres.
 */
@QuarkusTest
class PgSoulStoreTest {

  @Inject
  SoulStore store;

  @AfterEach
  @Transactional
  void resetSoul() {
    SoulResetHelper.resetToDefaults();
  }

  @Test
  @Transactional
  void loadReturnsNonNullPersona() {
    assertNotNull(store.load(), "a persona row should be seeded on first load");
  }

  @Test
  @Transactional
  void saveThenLoadRoundTrips() {
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
        "core identity with its own headers must survive the round-trip");
  }
}
