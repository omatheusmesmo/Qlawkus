package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * The language a live session has settled on. A single utterance is too short to trust, so the session
 * language is the one the last few utterances agree on most, and it needs at least two votes. A tie goes
 * to the language heard most recently.
 */
final class LanguageTracker {

    private static final int WINDOW = 5;
    private static final int MIN_VOTES = 2;

    private final Deque<String> recent = new ArrayDeque<>();

    synchronized void observe(String language) {
        recent.addLast(language);
        if (recent.size() > WINDOW) {
            recent.removeFirst();
        }
    }

    synchronized Optional<String> stable() {
        Map<String, Integer> votes = new HashMap<>();
        recent.forEach(language -> votes.merge(language, 1, Integer::sum));
        String best = null;
        int bestVotes = 0;
        Iterator<String> newestFirst = recent.descendingIterator();
        while (newestFirst.hasNext()) {
            String language = newestFirst.next();
            int count = votes.get(language);
            if (count > bestVotes) {
                best = language;
                bestVotes = count;
            }
        }
        return bestVotes >= MIN_VOTES ? Optional.of(best) : Optional.empty();
    }
}
