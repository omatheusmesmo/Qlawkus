package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuts a streamed reply into sentences so each one can be synthesized as soon as it is complete.
 * A sentence ends at {@code . ! ?} followed by whitespace, so a terminator at the very end of the
 * text waits for the next character. A run longer than the limit is cut at its last space, or at
 * the limit when it has none, so speech never waits forever on a long unpunctuated stretch.
 */
final class SentenceSplitter {

    private final int maxChars;
    private final StringBuilder pending = new StringBuilder();

    SentenceSplitter(int maxChars) {
        this.maxChars = maxChars;
    }

    List<String> accept(String chunk) {
        pending.append(chunk);
        List<String> out = new ArrayList<>();
        int end = sentenceEnd();
        while (end >= 0) {
            out.add(take(end));
            end = sentenceEnd();
        }
        while (pending.length() > maxChars) {
            int cut = lastSpaceWithin(maxChars);
            out.add(take(cut > 0 ? cut : maxChars));
        }
        return out;
    }

    Optional<String> flush() {
        String rest = pending.toString().trim();
        pending.setLength(0);
        return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
    }

    private int sentenceEnd() {
        for (int i = 0; i + 1 < pending.length(); i++) {
            if (isTerminator(pending.charAt(i)) && Character.isWhitespace(pending.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

    private int lastSpaceWithin(int limit) {
        for (int i = Math.min(limit, pending.length() - 1); i > 0; i--) {
            if (Character.isWhitespace(pending.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private String take(int end) {
        String sentence = pending.substring(0, end).trim();
        pending.delete(0, end);
        while (pending.length() > 0 && Character.isWhitespace(pending.charAt(0))) {
            pending.deleteCharAt(0);
        }
        return sentence;
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '!' || c == '?';
    }
}
