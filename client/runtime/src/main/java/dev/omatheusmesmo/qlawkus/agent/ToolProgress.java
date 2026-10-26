package dev.omatheusmesmo.qlawkus.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * How much of the agent's tool activity a channel shows while a turn runs. The same four names are
 * used by every channel that renders progress (the console, and the messaging adapters), so an
 * operator sets one vocabulary everywhere.
 *
 * <p>{@link #OFF} shows no tool activity, {@link #STATUS} shows which tool ran and for how long,
 * {@link #PREVIEW} adds the first characters of its arguments, and {@link #VERBOSE} adds the
 * arguments up to a longer cap. Tool results are never part of any mode: they can be large and carry
 * third-party text.
 */
public enum ToolProgress {
    OFF,
    STATUS,
    PREVIEW,
    VERBOSE;

    /** The mode used when a request or an operator does not choose one. */
    public static final ToolProgress DEFAULT = PREVIEW;

    /** Characters of the arguments {@link #PREVIEW} keeps. */
    public static final int PREVIEW_CHARS = 40;

    /** Characters of the arguments {@link #VERBOSE} keeps, and the most any consumer ever holds. */
    public static final int VERBOSE_CHARS = 300;

    private static final String MARKER = "...";

    /**
     * Reads a mode name, ignoring case. A blank or missing name is {@link #DEFAULT}; an unknown one
     * is rejected so a typo in a request or a config file is not silently treated as a mode.
     */
    @JsonCreator
    public static ToolProgress parse(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        return valueOf(name.strip().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether this mode shows individual tool calls at all. */
    public boolean showsTools() {
        return this != OFF;
    }

    /**
     * The part of {@code arguments} this mode shows: nothing for {@link #OFF} and {@link #STATUS},
     * the leading characters for {@link #PREVIEW} and {@link #VERBOSE}.
     */
    public String detail(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        return switch (this) {
            case OFF, STATUS -> null;
            case PREVIEW -> truncate(arguments, PREVIEW_CHARS);
            case VERBOSE -> truncate(arguments, VERBOSE_CHARS);
        };
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = max;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + MARKER;
    }
}
