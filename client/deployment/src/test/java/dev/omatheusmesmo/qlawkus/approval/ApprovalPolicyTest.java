package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    @Test
    void withNothingConfiguredNoToolWaitsForTheOwner() {
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of());

        assertFalse(policy.requires("sendEmail"));
    }

    @Test
    void aToolNamedInTheConfigurationWaitsForTheOwner() {
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))), List.of());

        assertTrue(policy.requires("sendEmail"));
        assertFalse(policy.requires("readFile"));
    }

    @Test
    void aToolAnExtensionAsksForWaitsForTheOwnerEvenWhenTheConfigurationIsEmpty() {
        ApprovalRequirement fromExtension = name -> name.equals("installSkill");
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.empty()), List.of(fromExtension));

        assertTrue(policy.requires("installSkill"));
        assertFalse(policy.requires("searchSkillHub"));
    }

    @Test
    void oneRequirementSayingNoNeverLiftsAnotherSourcesYes() {
        ApprovalRequirement declines = name -> false;
        ApprovalPolicy policy = new ApprovalPolicy(config(Optional.of(List.of("sendEmail"))), List.of(declines));

        assertTrue(policy.requires("sendEmail"));
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
