package dev.omatheusmesmo.qlawkus.permission;

/**
 * The read side of the permission policy: what the owner decided for a tool. The tool catalog, the
 * approval gate and the approval service ask it, and none of them can change it.
 */
@FunctionalInterface
public interface ToolPermissions {

    /** The level in force for the tool with this name, as the model sees it. */
    PermissionLevel levelOf(String toolName);
}
