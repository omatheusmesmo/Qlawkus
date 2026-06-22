package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.pg.UserProfileEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test for the pgvector owner-profile backend ({@code PgUserProfileStore} via
 * {@code UserProfileEntity}), mirroring {@code MarkdownUserProfileStoreTest} on the database side:
 * the single profile row persists and reloads, and renders into its context block. Runs against Dev
 * Services Postgres.
 */
@QuarkusTest
class PgUserProfileStoreTest {

  @Inject
  UserProfileStore store;

  @AfterEach
  @Transactional
  void resetProfile() {
    UserProfileEntity p = UserProfileEntity.findProfile();
    if (p != null) {
      p.name = null;
      p.profile = null;
    }
  }

  @Test
  @Transactional
  void loadReturnsNonNullProfile() {
    assertNotNull(store.load(), "a profile row should be seeded on first load");
  }

  @Test
  @Transactional
  void saveThenLoadRoundTrips() {
    UserProfile profile = store.load();
    profile.rename("Matheus");
    profile.rewriteProfile("Works with Java and Quarkus.\nPrefers constructor injection.");
    store.save(profile);

    UserProfile reloaded = store.load();
    assertEquals("Matheus", reloaded.name);
    assertTrue(reloaded.profile.contains("constructor injection"));
    assertTrue(reloaded.toContextBlock().contains("Matheus"));
  }
}
