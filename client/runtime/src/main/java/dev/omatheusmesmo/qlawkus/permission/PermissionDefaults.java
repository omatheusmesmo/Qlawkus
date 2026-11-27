package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.List;

/**
 * Lets an extension suggest a level for a cell of its own group, the way an extension used to publish an
 * {@code ApprovalRequirement} for a fixed list of tool names. A suggestion is the weakest voice in the
 * policy: it applies only where the owner has set neither the group's cell nor the operation's default,
 * so any choice the owner makes, a preset included, replaces it.
 */
public interface PermissionDefaults {

    /** The cells this extension suggests right now, read on every decision so a switch takes effect at once. */
    List<Cell> cells();

    /**
     * One suggested cell.
     *
     * @param group the group the extension's tools belong to
     * @param operation the operation within the group
     * @param level the suggested level
     */
    record Cell(String group, AccessClass operation, PermissionLevel level) {
    }
}
