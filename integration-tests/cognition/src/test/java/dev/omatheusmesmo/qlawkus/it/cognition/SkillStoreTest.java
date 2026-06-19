package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Boots the full extension against a real Postgres (Dev Services) to verify the skill subsystem
 * wiring that unit tests cannot: backend selection, the pgvector store + V6 migration, and the
 * build-time bundled-skill pipeline surfacing in the live index. No LLM is needed.
 */
@QuarkusTest
class SkillStoreTest {

  @Inject
  SkillStore skillStore;

  @Test
  void defaultBackend_isPgvector() {
    assertTrue(skillStore.getClass().getName().contains("PgSkillStore"),
        "default backend should resolve to PgSkillStore, was " + skillStore.getClass().getName());
  }

  @Test
  void crud_roundTripAgainstDatabase() {
    skillStore.save(new Skill("it-skill", "an integration test skill", "1. do the thing"));

    assertTrue(skillStore.get("it-skill").isPresent());
    assertEquals("an integration test skill", skillStore.get("it-skill").get().description());
    assertTrue(skillStore.index().stream().anyMatch(s -> s.name().equals("it-skill")));

    assertTrue(skillStore.delete("it-skill"));
    assertTrue(skillStore.get("it-skill").isEmpty());
  }

  @Test
  void bundledSkill_discoveredAtBuildTime_isInLiveIndex() {
    assertTrue(skillStore.index().stream().anyMatch(s -> s.name().equals("it-bundled")),
        "bundled skill should be merged into the live index");
    assertTrue(skillStore.get("it-bundled").isPresent());
    assertTrue(skillStore.get("it-bundled").get().body().contains("IT Bundled Skill"));
  }
}
