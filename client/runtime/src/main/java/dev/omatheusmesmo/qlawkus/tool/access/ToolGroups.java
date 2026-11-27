package dev.omatheusmesmo.qlawkus.tool.access;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Derives the permission group of a tool from where its bean lives, so the owner can say "calendar"
 * instead of listing tools. The group is the first package segment after the Qlawkus base package once
 * a leading {@code tool} or {@code tools} segment and a following {@code google} segment are dropped:
 * {@code ...tools.google.calendar.CalendarTool} is {@code calendar}, {@code ...tool.shell.ShellTool} is
 * {@code shell}, {@code ...task.TaskSchedulerTool} is {@code task}. A bean outside the base package
 * uses the last segment of its own package, and a tool the build did not see (a remote MCP tool) is in
 * {@link #EXTERNAL}.
 */
public final class ToolGroups {

    public static final String EXTERNAL = "external";
    public static final String CORE = "core";

    private static final String BASE = "dev.omatheusmesmo.qlawkus.";
    private static final List<String> SKIPPED_PREFIXES = List.of("tools", "tool");
    private static final String VENDOR = "google";

    private ToolGroups() {
    }

    /** The group of a tool the build classified. */
    public static String of(ToolAccessEntry entry) {
        return fromOrigin(entry.origin());
    }

    /** The group for an origin of the form {@code package.Bean#method}, or {@link #EXTERNAL} when it has no package. */
    public static String fromOrigin(String origin) {
        if (origin == null) {
            return EXTERNAL;
        }
        int hash = origin.indexOf('#');
        String bean = hash < 0 ? origin : origin.substring(0, hash);
        int lastDot = bean.lastIndexOf('.');
        if (lastDot < 0) {
            return EXTERNAL;
        }
        String packageName = bean.substring(0, lastDot);
        if (!packageName.startsWith(BASE.substring(0, BASE.length() - 1))) {
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        }
        String relative = packageName.length() < BASE.length() ? "" : packageName.substring(BASE.length());
        List<String> segments = new ArrayList<>(Arrays.asList(relative.split("\\.")));
        segments.removeIf(String::isEmpty);
        if (!segments.isEmpty() && SKIPPED_PREFIXES.contains(segments.get(0))) {
            segments.remove(0);
        }
        if (!segments.isEmpty() && VENDOR.equals(segments.get(0))) {
            segments.remove(0);
        }
        return segments.isEmpty() ? CORE : segments.get(0);
    }
}
