package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionOverview;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One row of the permission matrix, with a cell for each operation keyed by its lower-case name.
 */
public record PermissionGroupView(String group, Map<String, PermissionCellView> cells) {

    public static PermissionGroupView of(String group, Map<AccessClass, PermissionOverview.Cell> cells) {
        Map<String, PermissionCellView> view = new LinkedHashMap<>();
        for (AccessClass operation : AccessClass.values()) {
            view.put(operation.name().toLowerCase(Locale.ROOT), PermissionCellView.of(cells.get(operation)));
        }
        return new PermissionGroupView(group, view);
    }
}
