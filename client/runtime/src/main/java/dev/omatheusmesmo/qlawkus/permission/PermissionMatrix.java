package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import dev.omatheusmesmo.qlawkus.tool.access.ToolGroups;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves the level of a tool from the layers of the policy, most specific first:
 *
 * <ol>
 *   <li>the exception for the exact tool name (runtime, then configuration)</li>
 *   <li>the cell for the tool's group and operation (runtime, then configuration)</li>
 *   <li>the default for the operation, which a preset also fills (runtime, then configuration)</li>
 *   <li>what an extension suggests for its own group and operation ({@link PermissionDefaults})</li>
 *   <li>{@code allow}</li>
 * </ol>
 *
 * <p>A tool the build did not classify is {@link AccessClass#EXECUTE} in the {@link ToolGroups#EXTERNAL}
 * group, so it is covered by the strictest operation default. The matrix is a pure function of its
 * inputs and reads nothing at runtime beyond the extension suggestions.
 */
public final class PermissionMatrix {

    /** Which layer decided. */
    public enum Layer {
        TOOL_RUNTIME,
        TOOL_CONFIG,
        CELL_RUNTIME,
        CELL_CONFIG,
        DEFAULT_RUNTIME,
        DEFAULT_CONFIG,
        EXTENSION,
        BUILT_IN
    }

    /**
     * A level and the layer that produced it.
     *
     * @param basis a sentence fragment saying where the level came from
     */
    public record Decision(PermissionLevel level, Layer layer, String basis) {
    }

    /** The decision for one tool together with how the tool was classified. */
    public record ToolResolution(String tool, String group, AccessClass accessClass, Decision decision) {

        public PermissionLevel level() {
            return decision.level();
        }
    }

    private final PermissionState configured;
    private final PermissionState runtime;
    private final Iterable<PermissionDefaults> extensions;
    private final ToolAccessRegistry registry;

    public PermissionMatrix(PermissionState configured, PermissionState runtime,
            Iterable<PermissionDefaults> extensions, ToolAccessRegistry registry) {
        this.configured = configured;
        this.runtime = runtime;
        this.extensions = extensions;
        this.registry = registry;
    }

    public ToolResolution resolve(String tool) {
        String group = groupOf(tool);
        AccessClass accessClass = registry.classOf(tool);
        Decision decision = toolException(tool).orElseGet(() -> cell(group, accessClass));
        return new ToolResolution(tool, group, accessClass, decision);
    }

    /** The level of an operation within a group, ignoring per-tool exceptions. */
    public Decision cell(String group, AccessClass operation) {
        PermissionLevel level = runtime.groups().getOrDefault(group, Map.of()).get(operation);
        if (level != null) {
            return new Decision(level, Layer.CELL_RUNTIME,
                    "the " + group + " " + name(operation) + " cell, set at runtime" + presetSuffix(runtime));
        }
        level = configured.groups().getOrDefault(group, Map.of()).get(operation);
        if (level != null) {
            return new Decision(level, Layer.CELL_CONFIG,
                    "the " + group + " " + name(operation) + " cell, from the configuration");
        }
        return operationDefault(group, operation);
    }

    /** The level of an operation in any group, with no group or extension suggestion applied. */
    public Decision operationDefault(AccessClass operation) {
        return explicitDefault(operation).orElseGet(() -> builtIn());
    }

    private Decision operationDefault(String group, AccessClass operation) {
        Optional<Decision> explicit = explicitDefault(operation);
        if (explicit.isPresent()) {
            return explicit.get();
        }
        for (PermissionDefaults extension : extensions) {
            for (PermissionDefaults.Cell cell : extension.cells()) {
                if (cell.group().equals(group) && cell.operation() == operation) {
                    return new Decision(cell.level(), Layer.EXTENSION,
                            "the default the " + group + " extension suggests for " + name(operation));
                }
            }
        }
        return builtIn();
    }

    private Optional<Decision> explicitDefault(AccessClass operation) {
        PermissionLevel level = runtime.defaults().get(operation);
        if (level != null) {
            return Optional.of(new Decision(level, Layer.DEFAULT_RUNTIME,
                    "the " + name(operation) + " default, set at runtime" + presetSuffix(runtime)));
        }
        level = configured.defaults().get(operation);
        if (level != null) {
            return Optional.of(new Decision(level, Layer.DEFAULT_CONFIG,
                    "the " + name(operation) + " default, from the configuration" + presetSuffix(configured)));
        }
        return Optional.empty();
    }

    private Optional<Decision> toolException(String tool) {
        PermissionLevel level = runtime.tools().get(tool);
        if (level != null) {
            return Optional.of(new Decision(level, Layer.TOOL_RUNTIME, "the exception for the tool " + tool
                    + ", set at runtime"));
        }
        level = configured.tools().get(tool);
        if (level != null) {
            return Optional.of(new Decision(level, Layer.TOOL_CONFIG, "the exception for the tool " + tool
                    + ", from the configuration"));
        }
        return Optional.empty();
    }

    private static Decision builtIn() {
        return new Decision(PermissionLevel.ALLOW, Layer.BUILT_IN, "nothing restricts it, so it is allowed");
    }

    private static String presetSuffix(PermissionState state) {
        return state.preset() == null ? "" : " (preset " + state.preset() + ")";
    }

    private static String name(AccessClass operation) {
        return operation.name().toLowerCase(Locale.ROOT);
    }

    /** The group of a tool: the one derived at build time, or {@link ToolGroups#EXTERNAL}. */
    public String groupOf(String tool) {
        return registry.entry(tool).map(ToolGroups::of).orElse(ToolGroups.EXTERNAL);
    }

    /** Every group a cell can be set for, in name order. */
    public Set<String> groups() {
        Set<String> groups = new TreeSet<>();
        groups.add(ToolGroups.EXTERNAL);
        registry.entries().forEach(entry -> groups.add(ToolGroups.of(entry)));
        for (PermissionDefaults extension : extensions) {
            extension.cells().forEach(cell -> groups.add(cell.group()));
        }
        groups.addAll(configured.groups().keySet());
        groups.addAll(runtime.groups().keySet());
        return groups;
    }

    /** Every tool the matrix can say something about: the classified ones and the ones with an exception. */
    public Set<String> tools() {
        Set<String> tools = new TreeSet<>();
        registry.entries().forEach(entry -> tools.add(entry.tool()));
        tools.addAll(configured.tools().keySet());
        tools.addAll(runtime.tools().keySet());
        return tools;
    }

    /** How many classified tools sit in this cell, so a grid can show which cells matter. */
    public int toolCount(String group, AccessClass operation) {
        return (int) registry.entries().stream()
                .filter(entry -> entry.accessClass() == operation && ToolGroups.of(entry).equals(group))
                .count();
    }

    public ToolAccessRegistry registry() {
        return registry;
    }
}
