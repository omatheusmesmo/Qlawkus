package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillHubApprovalRequirementTest {

  private static final List<String> WRITES = List.of("installSkill", "installSkillsFromSource",
      "installSkillFromText", "updateSkill", "publishSkill");

  @Test
  void inHitlEveryWritingToolWaitsForTheOwner() {
    SkillHubApprovalRequirement requirement = requirement(ApprovalMode.HITL, true);

    for (String tool : WRITES) {
      assertTrue(requirement.requires(tool), tool);
    }
  }

  @Test
  void theReadOnlyToolsNeverWait() {
    SkillHubApprovalRequirement requirement = requirement(ApprovalMode.HITL, true);

    assertFalse(requirement.requires("searchSkillHub"));
    assertFalse(requirement.requires("listSkillsInSource"));
    assertFalse(requirement.requires("listSkillSources"));
  }

  @Test
  void inYoloNothingWaits() {
    SkillHubApprovalRequirement requirement = requirement(ApprovalMode.YOLO, true);

    for (String tool : WRITES) {
      assertFalse(requirement.requires(tool), tool);
    }
  }

  @Test
  void aToolOfAnotherExtensionIsNotClaimed() {
    assertFalse(requirement(ApprovalMode.HITL, true).requires("sendEmail"));
  }

  private static SkillHubApprovalRequirement requirement(ApprovalMode mode, boolean enabled) {
    return new SkillHubApprovalRequirement(new SkillHubToolTest.TestConfig(enabled, true, mode, 10));
  }
}
