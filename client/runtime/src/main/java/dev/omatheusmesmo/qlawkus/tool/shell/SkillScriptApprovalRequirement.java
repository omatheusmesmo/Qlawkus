package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.approval.ApprovalConfig;
import dev.omatheusmesmo.qlawkus.approval.ApprovalRequirement;
import dev.omatheusmesmo.qlawkus.config.SkillScriptsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Puts the skill script tools behind the owner's approval where they must be. Every run of an installed
 * skill's script waits while {@code qlawkus.skill-scripts.installed-require-approval} is on, and the
 * agent's own scripts wait whenever the owner gated {@code runCommand}, because they run through it and
 * must not be a way around that gate.
 */
@ApplicationScoped
public class SkillScriptApprovalRequirement implements ApprovalRequirement {

    private final SkillScriptsConfig scripts;
    private final ApprovalConfig approval;

    @Inject
    public SkillScriptApprovalRequirement(SkillScriptsConfig scripts, ApprovalConfig approval) {
        this.scripts = scripts;
        this.approval = approval;
    }

    @Override
    public boolean requires(String toolName) {
        if (SkillScriptTool.INSTALLED_TOOL.equals(toolName)) {
            return scripts.installedRequireApproval()
                    || approval.tools().orElse(List.of()).contains(SkillScriptTool.SHELL_TOOL);
        }
        if (SkillScriptTool.OWN_TOOL.equals(toolName)) {
            return approval.tools().orElse(List.of()).contains(SkillScriptTool.SHELL_TOOL);
        }
        return false;
    }
}
