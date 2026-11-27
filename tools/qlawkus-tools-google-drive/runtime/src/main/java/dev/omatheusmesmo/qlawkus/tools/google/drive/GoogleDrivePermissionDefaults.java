package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.permission.PermissionDefaults;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Suggests {@code ask} for the {@code delete} cell of the {@code drive} group while
 * {@code qlawkus.google.drive.trash-needs-approval} is on, which is {@code trashFile}. It is a default:
 * an operator's own matrix entry for the group or the operation wins over it. Restoring a file and every
 * read are never suggested.
 */
@ApplicationScoped
public class GoogleDrivePermissionDefaults implements PermissionDefaults {

    static final String GROUP = "drive";

    private final GoogleDriveConfig config;

    @Inject
    public GoogleDrivePermissionDefaults(GoogleDriveConfig config) {
        this.config = config;
    }

    @Override
    public List<Cell> cells() {
        if (!config.enabled() || !config.trashNeedsApproval()) {
            return List.of();
        }
        return List.of(new Cell(GROUP, AccessClass.DELETE, PermissionLevel.ASK));
    }
}
