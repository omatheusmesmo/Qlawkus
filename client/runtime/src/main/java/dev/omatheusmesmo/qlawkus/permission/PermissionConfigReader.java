package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns {@link PermissionsConfig} into the configuration layer of the policy. A misspelt operation or
 * preset is an error at start rather than a silently ignored line, since an ignored {@code deny} would
 * leave the agent more capable than the owner meant.
 */
final class PermissionConfigReader {

    private PermissionConfigReader() {
    }

    static PermissionState read(PermissionsConfig config) {
        Map<AccessClass, PermissionLevel> defaults = new EnumMap<>(AccessClass.class);
        String preset = config.preset().map(String::strip).filter(name -> !name.isEmpty()).orElse(null);
        if (preset != null) {
            defaults.putAll(PermissionPresets.find(preset).orElseThrow(() -> new IllegalArgumentException(
                    "qlawkus.permissions.preset=" + preset + " is not a preset; use "
                            + String.join(", ", PermissionPresets.names()))));
        }
        config.defaults().forEach((operation, level) -> defaults.put(operation(operation,
                "qlawkus.permissions.default"), level));
        Map<String, Map<AccessClass, PermissionLevel>> groups = new LinkedHashMap<>();
        config.group().forEach((group, cells) -> {
            Map<AccessClass, PermissionLevel> parsed = new EnumMap<>(AccessClass.class);
            cells.forEach((operation, level) -> parsed.put(operation(operation,
                    "qlawkus.permissions.group." + group), level));
            groups.put(group, parsed);
        });
        return new PermissionState(preset, defaults, groups, config.tool(), 0);
    }

    static Optional<AccessClass> parseOperation(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toUpperCase(Locale.ROOT);
        for (AccessClass accessClass : AccessClass.values()) {
            if (accessClass.name().equals(wanted)) {
                return Optional.of(accessClass);
            }
        }
        return Optional.empty();
    }

    private static AccessClass operation(String text, String where) {
        return parseOperation(text).orElseThrow(() -> new IllegalArgumentException(where + "." + text
                + " is not an operation; use read, create, update, delete, send or execute"));
    }
}
