package dev.omatheusmesmo.qlawkus.console.management;

import java.util.Set;

/**
 * Maps a MicroProfile config source name to the short label an operator reads. The full name stays
 * available to the page as a tooltip, since the raw form carries jar paths and class names.
 */
public final class ConfigSourceLabel {

    private static final String DEFAULT = "default";
    private static final String SOURCE_MARKER = "[source=";

    private ConfigSourceLabel() {
    }

    private static final Set<String> OWNER_SOURCES = Set.of("env", "system property", "override file", "keystore",
            "agent.yml");

    public static boolean isDefault(String sourceName) {
        return DEFAULT.equals(of(sourceName));
    }

    /**
     * True when the value comes from a source the owner controls at deploy or run time, as opposed to the
     * defaults the distribution and its extensions ship in their own property files.
     */
    public static boolean isOwnerSource(String sourceName) {
        return OWNER_SOURCES.contains(of(sourceName));
    }

    public static String of(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return DEFAULT;
        }
        String name = sourceName.strip();
        if (name.startsWith("DefaultValues")) {
            return DEFAULT;
        }
        if (name.startsWith("EnvConfigSource")) {
            return "env";
        }
        if (name.startsWith("SysPropConfigSource")) {
            return "system property";
        }
        return switch (name) {
            case "QlawkusKeystoreSecrets" -> "keystore";
            case "QlawkusRuntimeTogglesOverride" -> "override file";
            case "QlawkusRuntimeTogglesBaked" -> "agent.yml";
            default -> fromLocation(name);
        };
    }

    private static String fromLocation(String name) {
        int marker = name.indexOf(SOURCE_MARKER);
        if (marker < 0) {
            return name;
        }
        int start = marker + SOURCE_MARKER.length();
        int end = name.lastIndexOf(']');
        String location = end > start ? name.substring(start, end) : name.substring(start);
        String file = location.substring(location.lastIndexOf('/') + 1);
        return file.isBlank() ? name : file;
    }
}
