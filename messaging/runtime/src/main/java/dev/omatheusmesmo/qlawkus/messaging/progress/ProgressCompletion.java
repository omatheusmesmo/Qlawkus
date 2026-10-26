package dev.omatheusmesmo.qlawkus.messaging.progress;

import java.util.Locale;

public enum ProgressCompletion {
    SUMMARY,
    DELETE;

    public static ProgressCompletion parse(String name) {
        return valueOf(name.strip().toUpperCase(Locale.ROOT));
    }
}
