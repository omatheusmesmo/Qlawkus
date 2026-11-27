package dev.omatheusmesmo.qlawkus.tools.google.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.permission.PermissionDefaults;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleDrivePermissionDefaultsTest {

    @Test
    void deletingAsksTheOwnerWhileTheSwitchIsOn() {
        assertEquals(List.of(new PermissionDefaults.Cell("drive", AccessClass.DELETE, PermissionLevel.ASK)),
                defaults(true, true).cells());
    }

    @Test
    void turningTheSwitchOffOrDisablingDriveRemovesTheDefault() {
        assertTrue(defaults(true, false).cells().isEmpty());
        assertTrue(defaults(false, true).cells().isEmpty());
    }

    private static GoogleDrivePermissionDefaults defaults(boolean enabled, boolean trashNeedsApproval) {
        return new GoogleDrivePermissionDefaults(new GoogleDriveConfig() {
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
