package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.permission.ToolPermissions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    private static final ToolPermissions ALLOW_ALL = tool -> PermissionLevel.ALLOW;

    @Test
    void withNothingConfiguredNoToolWaitsForTheOwner() {
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of(), ALLOW_ALL);

        assertFalse(policy.requires("sendEmail"));
    }

    @Test
    void aToolNamedInTheConfigurationWaitsForTheOwner() {
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))), List.of(), ALLOW_ALL);

        assertTrue(policy.requires("sendEmail"));
        assertFalse(policy.requires("readFile"));
    }

    @Test
    void aToolAnExtensionAsksForWaitsForTheOwnerEvenWhenTheConfigurationIsEmpty() {
        ApprovalRequirement fromExtension = name -> name.equals("installSkill");
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of(fromExtension), ALLOW_ALL);

        assertTrue(policy.requires("installSkill"));
        assertFalse(policy.requires("searchSkillHub"));
    }

    @Test
    void oneRequirementSayingNoNeverLiftsAnotherSourcesYes() {
        ApprovalRequirement declines = name -> false;
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))), List.of(declines), ALLOW_ALL);

        assertTrue(policy.requires("sendEmail"));
    }

    @Test
    void aToolTheMatrixSaysAskForWaitsForTheOwner() {
        ToolPermissions matrix = tool -> tool.equals("deleteEvent") ? PermissionLevel.ASK : PermissionLevel.ALLOW;
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of(), matrix);

        assertTrue(policy.requires("deleteEvent"));
        assertFalse(policy.requires("listEvents"));
    }

    @Test
    void theMatrixSayingAllowNeverLiftsAToolTheApprovalListNamed() {
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))), List.of(), ALLOW_ALL);

        assertTrue(policy.requires("sendEmail"));
    }

    @Test
    void theMatrixSayingAllowNeverLiftsAnExtensionRequirement() {
        ApprovalRequirement fromExtension = name -> name.equals("installSkill");
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of(fromExtension), ALLOW_ALL);

        assertTrue(policy.requires("installSkill"));
    }

    @Test
    void theReasonNamesTheListOrTheExtensionThatAddsAsk() {
        ApprovalRequirement fromExtension = name -> name.equals("installSkill");
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))),
                List.of(fromExtension), ALLOW_ALL);

        assertTrue(policy.reasonFor("sendEmail").orElseThrow().contains("qlawkus.approval.tools"));
        assertTrue(policy.reasonFor("installSkill").orElseThrow().contains("extension"));
        assertTrue(policy.reasonFor("readFile").isEmpty());
    }

    private static ApprovalConfig config(Optional<List<String>> tools) {
        return new ApprovalConfig() {
            @Override
            public Optional<List<String>> tools() {
                return tools;
            }

            @Override
            public Duration expiry() {
                return Duration.ofHours(24);
            }

            @Override
            public int maxPending() {
                return 5;
            }

            @Override
            public int previewMaxChars() {
                return 200;
            }
        };
    }
}
