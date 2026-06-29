package dev.omatheusmesmo.qlawkus.composition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The default posture of every capability in the {@code build-time} section of {@code agent.yml}.
 *
 * <p>{@link #ENABLED} means "all capabilities on, list the ones to turn off"; {@link #DISABLED}
 * means "all off, list the ones to turn on". The {@code except} list always carries the opposite
 * effect of the posture (policy + exceptions).
 */
public enum Posture {

    ENABLED,
    DISABLED;

    /**
     * Parses the wire form ({@code enabled} / {@code disabled}, case-insensitive).
     *
     * @throws IllegalArgumentException if {@code raw} is null or not a known posture
     */
    @JsonCreator
    public static Posture from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("build-time.default must be 'enabled' or 'disabled'");
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "enabled" -> ENABLED;
            case "disabled" -> DISABLED;
            default -> throw new IllegalArgumentException(
                    "build-time.default must be 'enabled' or 'disabled', got: " + raw);
        };
    }

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
