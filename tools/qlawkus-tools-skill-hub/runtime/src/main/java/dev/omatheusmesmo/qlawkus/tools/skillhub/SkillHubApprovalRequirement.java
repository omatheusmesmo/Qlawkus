package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequirement;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig.ApprovalMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * Puts the Skill Hub's writing tools behind the owner's durable approval while
 * {@code qlawkus.skill-hub.approval-mode} is {@code hitl}. The read-only tools never wait, and in
 * {@code yolo} nothing does.
 */
@ApplicationScoped
public class SkillHubApprovalRequirement implements ApprovalRequirement {

  static final Set<String> WRITING_TOOLS = Set.of("installSkill", "installSkillsFromSource",
      "installSkillFromText", "updateSkill", "publishSkill");

  private final SkillHubConfig config;

  @Inject
  public SkillHubApprovalRequirement(SkillHubConfig config) {
    this.config = config;
  }

  @Override
  public boolean requires(String toolName) {
    return config.enabled() && config.approvalMode() == ApprovalMode.HITL
        && WRITING_TOOLS.contains(toolName);
  }
}
