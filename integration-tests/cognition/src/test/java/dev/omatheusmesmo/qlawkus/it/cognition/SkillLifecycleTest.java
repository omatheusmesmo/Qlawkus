package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the usage-telemetry lifecycle on the pgvector backend against a real database
 * (columns added by migration V7): unused skills archive out of the index, using revives them,
 * and pinned skills are never archived.
 */
@QuarkusTest
class SkillLifecycleTest {

  @Inject
  SkillStore skillStore;

  @AfterEach
  void cleanup() {
    skillStore.delete("life-archived");
    skillStore.delete("life-revived");
    skillStore.delete("life-pinned");
  }

  @Test
  void sweep_archivesUnused_excludedFromIndex_butStillLoadable() {
    skillStore.save(new Skill("life-archived", "desc", "body"));
    assertTrue(inIndex("life-archived"));

    skillStore.sweepLifecycle(0, 0);

    assertFalse(inIndex("life-archived"), "archived skill is excluded from the index");
    assertTrue(skillStore.get("life-archived").isPresent(), "archived skill is still loadable");
  }

  @Test
  void recordUse_revivesArchivedSkill() {
    skillStore.save(new Skill("life-revived", "desc", "body"));
    skillStore.sweepLifecycle(0, 0);
    assertFalse(inIndex("life-revived"));

    skillStore.recordUse("life-revived");

    assertTrue(inIndex("life-revived"), "a used skill returns to the index");
  }

  @Test
  void pinnedSkill_isNotArchived() {
    skillStore.save(new Skill("life-pinned", "desc", "body"));
    assertTrue(skillStore.setPinned("life-pinned", true));

    skillStore.sweepLifecycle(0, 0);

    assertTrue(inIndex("life-pinned"), "pinned skill survives the sweep");
  }

  private boolean inIndex(String name) {
    return skillStore.index().stream().anyMatch(summary -> summary.name().equals(name));
  }
}
