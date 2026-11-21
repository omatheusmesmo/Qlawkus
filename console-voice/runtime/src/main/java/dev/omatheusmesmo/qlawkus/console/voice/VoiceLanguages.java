package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Speech-provider choices and recognition hints; reply policy lives in the core resolver. */
final class VoiceLanguages {

    static final String AUTO = "auto";

    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}");

    private final List<String> speakable;
    private final String fallback;

    VoiceLanguages(Collection<String> speakable, String fallback) {
        this.speakable = List.copyOf(new LinkedHashSet<>(speakable));
        this.fallback = code(fallback).orElse(fallback);
    }

    static Optional<String> code(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        String primary = tag.trim().toLowerCase(Locale.ROOT);
        int separator = Math.max(primary.indexOf('-'), primary.indexOf('_'));
        if (separator > 0) {
            primary = primary.substring(0, separator);
        }
        return CODE.matcher(primary).matches() ? Optional.of(primary) : Optional.empty();
    }

    static Optional<String> choice(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase(AUTO)) {
            return Optional.of(AUTO);
        }
        return code(raw);
    }

    boolean accepts(String choice) {
        return AUTO.equals(choice) || speakable.contains(choice);
    }

    boolean speakable(String language) {
        return speakable.contains(language);
    }

    List<String> offered() {
        return speakable;
    }

    String fallback() {
        return fallback;
    }

    Optional<String> sttHint(String choice) {
        return AUTO.equals(choice) ? Optional.empty() : Optional.ofNullable(choice);
    }

}
