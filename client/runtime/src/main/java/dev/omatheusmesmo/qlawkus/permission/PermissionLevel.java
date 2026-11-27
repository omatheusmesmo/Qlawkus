package dev.omatheusmesmo.qlawkus.permission;

import java.util.Locale;
import java.util.Optional;

/**
 * What the owner has decided for one tool call: it runs, it waits for the owner's approval, or the tool
 * is not offered to the model at all. Ordered from the loosest to the strictest.
 */
public enum PermissionLevel {
    ALLOW,
    ASK,
    DENY;

    /** Whether this level restricts more than {@code other}. */
    public boolean isStricterThan(PermissionLevel other) {
        return ordinal() > other.ordinal();
    }

    /** The lower-case name used in configuration, the API and chat commands. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<PermissionLevel> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String wanted = text.strip().toUpperCase(Locale.ROOT);
        for (PermissionLevel level : values()) {
            if (level.name().equals(wanted)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }
}
