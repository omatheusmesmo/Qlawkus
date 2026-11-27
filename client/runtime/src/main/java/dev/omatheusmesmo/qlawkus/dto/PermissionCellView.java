package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionOverview;

/**
 * One cell of the permission matrix: the level in force, why, and how many tools sit in it.
 */
public record PermissionCellView(String level, String source, int tools) {

    public static PermissionCellView of(PermissionOverview.Cell cell) {
        return new PermissionCellView(cell.level().label(), cell.source(), cell.tools());
    }
}
