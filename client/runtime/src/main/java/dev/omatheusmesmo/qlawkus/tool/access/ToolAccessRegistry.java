package dev.omatheusmesmo.qlawkus.tool.access;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks up the {@link AccessClass} of a {@code @QlawTool} tool by the name the model sees. The build
 * fills it in, so nothing is read from the beans at runtime and it needs no reflection in a native
 * image. A tool the build did not see, such as one from a remote MCP server, is
 * {@link AccessClass#EXECUTE}, the most restrictive class.
 */
public final class ToolAccessRegistry {

    private final Map<String, ToolAccessEntry> byTool;

    public ToolAccessRegistry(List<ToolAccessEntry> entries) {
        this.byTool = entries.stream().collect(Collectors.toUnmodifiableMap(ToolAccessEntry::tool, Function.identity()));
    }

    public Optional<ToolAccessEntry> entry(String toolName) {
        return Optional.ofNullable(byTool.get(toolName));
    }

    public Optional<AccessClass> find(String toolName) {
        return entry(toolName).map(ToolAccessEntry::accessClass);
    }

    public AccessClass classOf(String toolName) {
        return find(toolName).orElse(AccessClass.EXECUTE);
    }

    public List<ToolAccessEntry> entries() {
        return byTool.values().stream().sorted(Comparator.comparing(ToolAccessEntry::tool)).toList();
    }
}
