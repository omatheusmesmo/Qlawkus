package dev.omatheusmesmo.qlawkus.messaging.transcription;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class WhisperLanguages {

    private static final Pattern ISO_CODE = Pattern.compile("[a-z]{2}");

    private static final Map<String, String> CODE_BY_NAME = Map.ofEntries(
            Map.entry("afrikaans", "af"), Map.entry("arabic", "ar"), Map.entry("armenian", "hy"),
            Map.entry("azerbaijani", "az"), Map.entry("basque", "eu"), Map.entry("belarusian", "be"),
            Map.entry("bengali", "bn"), Map.entry("bosnian", "bs"), Map.entry("bulgarian", "bg"),
            Map.entry("catalan", "ca"), Map.entry("chinese", "zh"), Map.entry("croatian", "hr"),
            Map.entry("czech", "cs"), Map.entry("danish", "da"), Map.entry("dutch", "nl"),
            Map.entry("english", "en"), Map.entry("estonian", "et"), Map.entry("finnish", "fi"),
            Map.entry("french", "fr"), Map.entry("galician", "gl"), Map.entry("georgian", "ka"),
            Map.entry("german", "de"), Map.entry("greek", "el"), Map.entry("gujarati", "gu"),
            Map.entry("hebrew", "he"), Map.entry("hindi", "hi"), Map.entry("hungarian", "hu"),
            Map.entry("icelandic", "is"), Map.entry("indonesian", "id"), Map.entry("italian", "it"),
            Map.entry("japanese", "ja"), Map.entry("kannada", "kn"), Map.entry("kazakh", "kk"),
            Map.entry("korean", "ko"), Map.entry("latvian", "lv"), Map.entry("lithuanian", "lt"),
            Map.entry("macedonian", "mk"), Map.entry("malay", "ms"), Map.entry("malayalam", "ml"),
            Map.entry("marathi", "mr"), Map.entry("nepali", "ne"), Map.entry("norwegian", "no"),
            Map.entry("persian", "fa"), Map.entry("polish", "pl"), Map.entry("portuguese", "pt"),
            Map.entry("punjabi", "pa"), Map.entry("romanian", "ro"), Map.entry("russian", "ru"),
            Map.entry("serbian", "sr"), Map.entry("slovak", "sk"), Map.entry("slovenian", "sl"),
            Map.entry("spanish", "es"), Map.entry("swahili", "sw"), Map.entry("swedish", "sv"),
            Map.entry("tagalog", "tl"), Map.entry("tamil", "ta"), Map.entry("telugu", "te"),
            Map.entry("thai", "th"), Map.entry("turkish", "tr"), Map.entry("ukrainian", "uk"),
            Map.entry("urdu", "ur"), Map.entry("vietnamese", "vi"), Map.entry("welsh", "cy"));

    private WhisperLanguages() {
    }

    static Optional<String> isoCode(String reported) {
        if (reported == null || reported.isBlank()) {
            return Optional.empty();
        }
        String value = reported.trim().toLowerCase(Locale.ROOT);
        if (ISO_CODE.matcher(value).matches()) {
            return Optional.of(value);
        }
        return Optional.ofNullable(CODE_BY_NAME.get(value));
    }

    static Optional<String> hint(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        String primary = language.trim().toLowerCase(Locale.ROOT);
        int separator = primary.indexOf('-');
        if (separator > 0) {
            primary = primary.substring(0, separator);
        }
        if (!ISO_CODE.matcher(primary).matches()) {
            throw new IllegalArgumentException("A transcription language hint must be an ISO 639-1 code");
        }
        return Optional.of(primary);
    }
}
