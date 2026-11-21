package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Guesses the language of a reply without a dependency: a non-Latin alphabet decides by its script, and
 * Latin text is scored by the most common function words of each candidate. A guess needs at least two
 * matching words and a clear lead over the runner-up, so a short or mixed reply is left to the fallback.
 */
final class ReplyLanguageDetector {

    private static final int MIN_EVIDENCE = 2;

    private static final Map<String, Set<String>> STOPWORDS = Map.of(
            "en", Set.of("the", "and", "is", "are", "you", "that", "with", "this", "what", "it", "of", "to",
                    "for", "have", "not", "can", "will", "your", "be", "was", "were", "they", "from", "about",
                    "there", "would", "should", "my", "we", "at", "or"),
            "pt", Set.of("não", "uma", "você", "isso", "estou", "são", "também", "obrigado", "obrigada",
                    "minha", "meu", "então", "muito", "mas", "ainda", "tudo", "bem", "ele", "ela", "nós", "eu",
                    "sou"),
            "es", Set.of("pero", "usted", "gracias", "hola", "muy", "también", "así", "eso", "estoy", "quiero",
                    "tengo", "los", "las", "el", "es", "son", "con"),
            "fr", Set.of("les", "des", "vous", "est", "merci", "avec", "pour", "dans", "pas", "bonjour", "je",
                    "suis", "très", "mais", "une", "ça"),
            "de", Set.of("und", "der", "die", "das", "ich", "nicht", "ist", "ein", "eine", "mit", "danke",
                    "bitte", "auch", "sie", "wir", "sind", "nein", "ja"),
            "it", Set.of("il", "che", "non", "sono", "della", "per", "grazie", "ciao", "anche", "questo",
                    "questa", "molto", "come", "è", "sei", "ma", "bene"));

    private static final Map<Character.UnicodeScript, String> LANGUAGE_BY_SCRIPT = Map.ofEntries(
            Map.entry(Character.UnicodeScript.CYRILLIC, "ru"),
            Map.entry(Character.UnicodeScript.GREEK, "el"),
            Map.entry(Character.UnicodeScript.ARABIC, "ar"),
            Map.entry(Character.UnicodeScript.HEBREW, "he"),
            Map.entry(Character.UnicodeScript.HANGUL, "ko"),
            Map.entry(Character.UnicodeScript.DEVANAGARI, "hi"),
            Map.entry(Character.UnicodeScript.THAI, "th"),
            Map.entry(Character.UnicodeScript.HAN, "zh"),
            Map.entry(Character.UnicodeScript.HIRAGANA, "ja"),
            Map.entry(Character.UnicodeScript.KATAKANA, "ja"));

    private ReplyLanguageDetector() {
    }

    static Optional<String> detect(String text, Collection<String> candidates) {
        if (text == null || text.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<Character.UnicodeScript, Integer> scripts = new EnumMap<>(Character.UnicodeScript.class);
        int latin = 0;
        int other = 0;
        for (int codePoint : text.codePoints().toArray()) {
            if (!Character.isLetter(codePoint)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.LATIN) {
                latin++;
            } else {
                scripts.merge(script, 1, Integer::sum);
                other++;
            }
        }
        if (other >= MIN_EVIDENCE && other > latin) {
            return byScript(scripts, candidates);
        }
        return byStopwords(text, candidates);
    }

    private static Optional<String> byScript(Map<Character.UnicodeScript, Integer> scripts,
                                             Collection<String> candidates) {
        Character.UnicodeScript dominant = scripts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
        String language = LANGUAGE_BY_SCRIPT.get(dominant);
        if (dominant == Character.UnicodeScript.HAN
                && (scripts.containsKey(Character.UnicodeScript.HIRAGANA)
                || scripts.containsKey(Character.UnicodeScript.KATAKANA))) {
            language = "ja";
        }
        return language != null && candidates.contains(language) ? Optional.of(language) : Optional.empty();
    }

    private static Optional<String> byStopwords(String text, Collection<String> candidates) {
        List<String> words = Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"));
        String best = null;
        int bestScore = 0;
        int runnerUp = 0;
        for (String candidate : candidates) {
            Set<String> stopwords = STOPWORDS.get(candidate);
            if (stopwords == null) {
                continue;
            }
            int score = (int) words.stream().filter(stopwords::contains).count();
            if (score > bestScore) {
                runnerUp = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score > runnerUp) {
                runnerUp = score;
            }
        }
        if (best == null || bestScore < MIN_EVIDENCE || bestScore == runnerUp) {
            return Optional.empty();
        }
        return Optional.of(best);
    }
}
