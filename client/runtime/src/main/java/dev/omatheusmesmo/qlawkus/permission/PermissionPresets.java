package dev.omatheusmesmo.qlawkus.permission;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The named starting points for the operation defaults, so the matrix does not have to be filled in
 * by hand. {@code readonly} lets the agent look and nothing else, {@code ask-writes} lets it read and
 * create and asks before it changes, removes, sends or executes anything, and {@code trusted} allows
 * everything.
 */
public final class PermissionPresets {

    public static final String READONLY = "readonly";
    public static final String ASK_WRITES = "ask-writes";
    public static final String TRUSTED = "trusted";

    private static final Map<String, Map<AccessClass, PermissionLevel>> PRESETS = Map.of(
            READONLY, levels(PermissionLevel.ALLOW, PermissionLevel.DENY, PermissionLevel.DENY,
                    PermissionLevel.DENY, PermissionLevel.DENY, PermissionLevel.DENY),
            ASK_WRITES, levels(PermissionLevel.ALLOW, PermissionLevel.ALLOW, PermissionLevel.ASK,
                    PermissionLevel.ASK, PermissionLevel.ASK, PermissionLevel.ASK),
            TRUSTED, levels(PermissionLevel.ALLOW, PermissionLevel.ALLOW, PermissionLevel.ALLOW,
                    PermissionLevel.ALLOW, PermissionLevel.ALLOW, PermissionLevel.ALLOW));

    private PermissionPresets() {
    }

    /** The operation defaults of the named preset, or empty for a name that is not a preset. */
    public static Optional<Map<AccessClass, PermissionLevel>> find(String name) {
        return Optional.ofNullable(name == null ? null : PRESETS.get(name.strip().toLowerCase(Locale.ROOT)));
    }

    public static List<String> names() {
        return List.of(READONLY, ASK_WRITES, TRUSTED);
    }

    private static Map<AccessClass, PermissionLevel> levels(PermissionLevel read, PermissionLevel create,
            PermissionLevel update, PermissionLevel delete, PermissionLevel send, PermissionLevel execute) {
        Map<AccessClass, PermissionLevel> levels = new EnumMap<>(AccessClass.class);
        levels.put(AccessClass.READ, read);
        levels.put(AccessClass.CREATE, create);
        levels.put(AccessClass.UPDATE, update);
        levels.put(AccessClass.DELETE, delete);
        levels.put(AccessClass.SEND, send);
        levels.put(AccessClass.EXECUTE, execute);
        return Map.copyOf(levels);
    }
}
