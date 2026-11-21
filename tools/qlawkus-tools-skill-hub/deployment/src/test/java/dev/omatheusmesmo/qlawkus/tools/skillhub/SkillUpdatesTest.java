package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.SkillOrigin;
import org.junit.jupiter.api.Test;

class SkillUpdatesTest {

  @Test
  void aSkillWithNoRecordedSourceCannotBeUpdated() {
    RecordingSkillStore store = new RecordingSkillStore();

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> SkillUpdates.recordedOrigin(store, "pasted"));

    assertTrue(refused.getMessage().contains("No recorded source"), refused.getMessage());
  }

  @Test
  void anUploadedSkillCannotBeFetchedAgainSoItCannotBeUpdated() {
    RecordingSkillStore store = new RecordingSkillStore();
    store.recordOrigin("triage", new SkillOrigin(SkillUpdates.UPLOAD_PREFIX + "bundle.zip", "triage", 1L));

    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> SkillUpdates.recordedOrigin(store, "triage"));

    assertTrue(refused.getMessage().contains("uploaded file"), refused.getMessage());
  }
}
