package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.permission.PermissionOverview;
import dev.omatheusmesmo.qlawkus.permission.PermissionPresets;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The API view of the whole permission matrix: the operation defaults as a row, one row per group, and
 * the per-tool exceptions.
 */
public record PermissionMatrixView(String preset, List<String> operations, List<String> presets,
        PermissionGroupView defaults, List<PermissionGroupView> groups, Map<String, String> tools) {

    public static PermissionMatrixView of(PermissionOverview overview) {
        Map<String, String> tools = new LinkedHashMap<>();
        overview.tools().forEach((tool, level) -> tools.put(tool, level.label()));
        return new PermissionMatrixView(overview.preset(),
                Arrays.stream(AccessClass.values()).map(operation -> operation.name().toLowerCase(Locale.ROOT)).toList(),
                PermissionPresets.names(), PermissionGroupView.of("default", overview.defaults()),
                overview.groups().stream().map(group -> PermissionGroupView.of(group.group(), group.cells())).toList(),
                tools);
    }
}
