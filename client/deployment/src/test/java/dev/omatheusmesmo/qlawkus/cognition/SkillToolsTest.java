package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import org.junit.jupiter.api.Test;

class SkillToolsTest {

  @Test
  void viewSkill_returnsBody_orNotFound() {
    InMemorySkillStore store = new InMemorySkillStore();
    store.save(new Skill("triage-inbox", "Triage inbox", "1. Open\n2. Sort"));
    ViewSkillTool tool = new ViewSkillTool(store);

    assertTrue(tool.viewSkill("triage-inbox").contains("Sort"));
    assertTrue(tool.viewSkill("missing").contains("No skill named"));
  }

  @Test
  void createOrUpdateSkill_savesToStore() {
    InMemorySkillStore store = new InMemorySkillStore();
    ManageSkillTool tool = new ManageSkillTool(store);

    String result = tool.createOrUpdateSkill("post-update", "Post weekly update", "do it");
    assertEquals("Saved skill: post-update", result);
    assertTrue(store.get("post-update").isPresent());
    assertEquals("Post weekly update", store.get("post-update").get().description());
  }

  @Test
  void createOrUpdateSkill_rejectsBlankName() {
    ManageSkillTool tool = new ManageSkillTool(new InMemorySkillStore());
    assertTrue(tool.createOrUpdateSkill("  ", "d", "b").contains("non-empty name"));
  }

  @Test
  void deleteSkill_removesOrReportsMissing() {
    InMemorySkillStore store = new InMemorySkillStore();
    store.save(new Skill("temp", "temporary", "body"));
    ManageSkillTool tool = new ManageSkillTool(store);

    assertEquals("Deleted skill: temp", tool.deleteSkill("temp"));
    assertFalse(store.get("temp").isPresent());
    assertTrue(tool.deleteSkill("temp").contains("No skill named"));
  }
}
