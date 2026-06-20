package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.SkillsConfig;
import dev.omatheusmesmo.qlawkus.skill.BundledSkills;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.store.pg.HybridSkillStore;
import dev.omatheusmesmo.qlawkus.store.pg.SkillEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the hybrid backend's dual write directly against a real Postgres: SKILL.md files are
 * the source of truth and every write is mirrored into the {@code skill} table. The store is
 * instantiated explicitly (rather than relying on build-time {@code @IfBuildProperty} selection, so
 * no separate hybrid-pinned module is needed); the untested risk is the file/mirror logic, not the
 * trivial bean selection. {@code qlawkus.skills.roots} is pinned to a build-dir path by the module's
 * test config. No LLM is needed.
 */
@QuarkusTest
class HybridSkillStoreTest {

  @Inject
  SkillsConfig config;

  @Inject
  BundledSkills bundled;

  private HybridSkillStore store;
  private Path root;

  @BeforeEach
  void setup() {
    store = new HybridSkillStore(config, bundled);
    root = Path.of(config.roots().get(0));
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() -> store.delete("hybrid-it"));
  }

  @Test
  void save_writesFile_andMirrorsToTable() {
    QuarkusTransaction.requiringNew()
        .run(() -> store.save(new Skill("hybrid-it", "a hybrid skill", "1. do the thing")));

    Path file = root.resolve("hybrid-it").resolve("SKILL.md");
    assertTrue(Files.exists(file), "SKILL.md should be written as the source of truth");
    assertTrue(mirrorExists("hybrid-it"), "skill should be mirrored into the skill table");

    assertTrue(QuarkusTransaction.requiringNew().call(() -> store.get("hybrid-it").isPresent()));
    assertEquals("a hybrid skill",
        QuarkusTransaction.requiringNew().call(() -> store.get("hybrid-it").get().description()));
  }

  @Test
  void delete_removesFile_andMirror() {
    QuarkusTransaction.requiringNew()
        .run(() -> store.save(new Skill("hybrid-it", "a hybrid skill", "1. step")));

    QuarkusTransaction.requiringNew().run(() -> store.delete("hybrid-it"));

    assertFalse(Files.exists(root.resolve("hybrid-it").resolve("SKILL.md")),
        "the file should be gone after delete");
    assertFalse(mirrorExists("hybrid-it"), "the mirror row should be gone after delete");
    assertTrue(QuarkusTransaction.requiringNew().call(() -> store.get("hybrid-it").isEmpty()));
  }

  private boolean mirrorExists(String name) {
    return QuarkusTransaction.requiringNew().call(() -> SkillEntity.findById(name) != null);
  }
}
