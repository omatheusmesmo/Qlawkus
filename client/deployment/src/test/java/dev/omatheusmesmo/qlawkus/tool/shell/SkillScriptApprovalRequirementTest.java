package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.approval.ApprovalConfig;
import dev.omatheusmesmo.qlawkus.config.SkillScriptsConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillScriptApprovalRequirementTest {

    private SkillScriptApprovalRequirement requirement(boolean installedRequireApproval, List<String> gated) {
        SkillScriptsConfig scripts = mock(SkillScriptsConfig.class);
        when(scripts.installedRequireApproval()).thenReturn(installedRequireApproval);
        ApprovalConfig approval = mock(ApprovalConfig.class);
        when(approval.tools()).thenReturn(Optional.of(gated));
        return new SkillScriptApprovalRequirement(scripts, approval);
    }

    @Test
    void installedScriptsWaitForTheOwnerByDefaultAndOwnScriptsDoNot() {
        SkillScriptApprovalRequirement requirement = requirement(true, List.of());

        assertTrue(requirement.requires(SkillScriptTool.INSTALLED_TOOL));
        assertFalse(requirement.requires(SkillScriptTool.OWN_TOOL));
        assertFalse(requirement.requires("viewSkill"));
    }

    @Test
    void theOwnerCanTurnTheInstalledScriptApprovalOff() {
        assertFalse(requirement(false, List.of()).requires(SkillScriptTool.INSTALLED_TOOL));
    }

    @Test
    void gatingTheShellGatesBothScriptToolsSoTheyAreNoWayAroundIt() {
        SkillScriptApprovalRequirement requirement = requirement(false, List.of("runCommand"));

        assertTrue(requirement.requires(SkillScriptTool.OWN_TOOL));
        assertTrue(requirement.requires(SkillScriptTool.INSTALLED_TOOL));
    }
}
