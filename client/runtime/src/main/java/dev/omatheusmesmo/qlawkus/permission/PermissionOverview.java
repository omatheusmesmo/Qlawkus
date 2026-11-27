package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.List;
import java.util.Map;

/**
 * A read of the whole matrix for a surface that shows it: the operation defaults, one row per group
 * with a cell per operation, and the per-tool exceptions.
 *
 * @param preset the preset the runtime state was last set from, or null when it is custom
 * @param defaults the level of each operation in any group
 * @param groups one row per known group, in name order
 * @param tools the per-tool exceptions in force, with their level
 */
public record PermissionOverview(String preset, Map<AccessClass, Cell> defaults, List<Group> groups,
        Map<String, PermissionLevel> tools) {

    /**
     * One cell of the grid.
     *
     * @param level the level in force
     * @param source why: the layer that decided
     * @param tools how many classified tools sit in the cell
     */
    public record Cell(PermissionLevel level, String source, int tools) {
    }

    /** One row of the grid. */
    public record Group(String group, Map<AccessClass, Cell> cells) {
    }
}
