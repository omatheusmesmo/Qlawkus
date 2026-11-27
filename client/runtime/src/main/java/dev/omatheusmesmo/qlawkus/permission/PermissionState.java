package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * One layer of the policy matrix as plain data: the preset it came from, the operation defaults, the
 * cells of each group, and the per-tool exceptions. The configuration is one layer and what the owner
 * changes at runtime is another; this record is immutable and every {@code with} method returns the
 * next state with the revision advanced.
 *
 * <p>Changing a single entry drops the preset name, because the state is no longer exactly that
 * preset. Applying a preset replaces the whole state, so no group cell or tool exception survives it.
 *
 * @param preset the preset the state was last set from, or null when it is custom
 * @param defaults the level for an operation in any group
 * @param groups the level for an operation in one group
 * @param tools the level for one tool by name
 * @param revision how many changes this state has seen, for compare-and-set
 */
public record PermissionState(String preset, Map<AccessClass, PermissionLevel> defaults,
        Map<String, Map<AccessClass, PermissionLevel>> groups, Map<String, PermissionLevel> tools, long revision) {

    public PermissionState {
        defaults = copyOf(defaults);
        groups = copyGroups(groups);
        tools = Map.copyOf(new TreeMap<>(tools));
    }

    public static PermissionState empty() {
        return new PermissionState(null, Map.of(), Map.of(), Map.of(), 0);
    }

    public PermissionState withDefault(AccessClass operation, PermissionLevel level) {
        Map<AccessClass, PermissionLevel> next = new EnumMap<>(AccessClass.class);
        next.putAll(defaults);
        next.put(operation, level);
        return new PermissionState(null, next, groups, tools, revision + 1);
    }

    public PermissionState withCell(String group, AccessClass operation, PermissionLevel level) {
        Map<String, Map<AccessClass, PermissionLevel>> next = new LinkedHashMap<>(groups);
        Map<AccessClass, PermissionLevel> cells = new EnumMap<>(AccessClass.class);
        cells.putAll(next.getOrDefault(group, Map.of()));
        cells.put(operation, level);
        next.put(group, cells);
        return new PermissionState(null, defaults, next, tools, revision + 1);
    }

    public PermissionState withTool(String tool, PermissionLevel level) {
        Map<String, PermissionLevel> next = new LinkedHashMap<>(tools);
        next.put(tool, level);
        return new PermissionState(null, defaults, groups, next, revision + 1);
    }

    /** Replaces everything with the operation defaults of a preset. */
    public PermissionState withPreset(String name, Map<AccessClass, PermissionLevel> levels) {
        return new PermissionState(name, levels, Map.of(), Map.of(), revision + 1);
    }

    /** The same entries with a fixed revision, for loading a persisted state. */
    public PermissionState atRevision(long next) {
        return new PermissionState(preset, defaults, groups, tools, next);
    }

    /** Drops every entry; the revision still advances so a pending change that expected the old state is refused. */
    public PermissionState cleared() {
        return new PermissionState(null, Map.of(), Map.of(), Map.of(), revision + 1);
    }

    public boolean isEmpty() {
        return defaults.isEmpty() && groups.isEmpty() && tools.isEmpty();
    }

    private static Map<AccessClass, PermissionLevel> copyOf(Map<AccessClass, PermissionLevel> source) {
        return source.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(source));
    }

    private static Map<String, Map<AccessClass, PermissionLevel>> copyGroups(
            Map<String, Map<AccessClass, PermissionLevel>> source) {
        Map<String, Map<AccessClass, PermissionLevel>> copy = new TreeMap<>();
        source.forEach((group, cells) -> copy.put(group, copyOf(cells)));
        return Map.copyOf(copy);
    }
}
