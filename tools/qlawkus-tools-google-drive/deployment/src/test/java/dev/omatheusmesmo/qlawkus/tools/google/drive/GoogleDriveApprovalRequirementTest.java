package dev.omatheusmesmo.qlawkus.tools.google.drive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GoogleDriveApprovalRequirementTest {

    @Test
    void trashingWaitsForTheOwnerWhileTheGateIsOn() {
        GoogleDriveApprovalRequirement requirement = requirement(true, true);

        assertTrue(requirement.requires("trashFile"));
    }

    @Test
    void everyOtherToolNeverWaits() {
        GoogleDriveApprovalRequirement requirement = requirement(true, true);

        assertFalse(requirement.requires("untrashFile"));
        assertFalse(requirement.requires("listDriveFiles"));
        assertFalse(requirement.requires("sendEmail"));
    }

    @Test
    void turningTheGateOffOrDisablingDriveReleasesTheTool() {
        assertFalse(requirement(true, false).requires("trashFile"));
        assertFalse(requirement(false, true).requires("trashFile"));
    }

    private static GoogleDriveApprovalRequirement requirement(boolean enabled, boolean trashNeedsApproval) {
        return new GoogleDriveApprovalRequirement(new GoogleDriveConfig() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public int maxResults() {
                return 10;
            }

            @Override
            public String fields() {
                return "id";
            }

            @Override
            public boolean trashNeedsApproval() {
                return trashNeedsApproval;
            }
        });
    }
}
