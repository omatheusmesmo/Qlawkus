package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.cognition.SkillCurationJob;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the curation job's execution (not just its name parser) with a mocked model, mirroring
 * {@link SkillExtractorObserverTest}: the model names a redundant skill and the job removes exactly
 * that one, leaving the rest, and removes nothing on NONE. Runs against the default pgvector store.
 */
@QuarkusTest
class SkillCurationJobTest {

  @InjectMock
  ChatModel chatModel;

  @Inject
  SkillCurationJob curationJob;

  @Inject
  SkillStore skillStore;

  @AfterEach
  void cleanup() {
    skillStore.delete("curate-keep");
    skillStore.delete("curate-dup");
  }

  @Test
  void curateNow_removesRedundant_keepsOthers() {
    skillStore.save(new Skill("curate-keep", "keep this one", "body a"));
    skillStore.save(new Skill("curate-dup", "a duplicate of keep", "body b"));
    when(chatModel.chat(anyString())).thenReturn("curate-dup");

    long removed = curationJob.curateNow();

    assertEquals(1, removed, "only the model-flagged duplicate should be removed");
    assertTrue(skillStore.get("curate-keep").isPresent(), "the kept skill must survive");
    assertTrue(skillStore.get("curate-dup").isEmpty(), "the redundant skill must be gone");
  }

  @Test
  void curateNow_none_removesNothing() {
    skillStore.save(new Skill("curate-keep", "keep this one", "body a"));
    skillStore.save(new Skill("curate-dup", "another distinct skill", "body b"));
    when(chatModel.chat(anyString())).thenReturn("NONE");

    assertEquals(0, curationJob.curateNow());
    assertTrue(skillStore.get("curate-keep").isPresent());
    assertTrue(skillStore.get("curate-dup").isPresent());
  }
}
