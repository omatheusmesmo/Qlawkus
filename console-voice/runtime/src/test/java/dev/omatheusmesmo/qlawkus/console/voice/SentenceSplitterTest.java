package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceSplitterTest {

    @Test
    void emitsASentenceOnceItsTerminatorIsFollowedByWhitespace() {
        SentenceSplitter splitter = new SentenceSplitter(200);

        assertEquals(List.of(), splitter.accept("Hello there."), "a terminator at the very end waits for the next character");
        assertEquals(List.of("Hello there."), splitter.accept(" How"));
        assertEquals("How", splitter.flush().orElseThrow());
    }

    @Test
    void splitsSeveralSentencesInOneChunk() {
        SentenceSplitter splitter = new SentenceSplitter(200);

        assertEquals(List.of("One.", "Two!", "Three?"), splitter.accept("One. Two! Three? Four"));
        assertEquals("Four", splitter.flush().orElseThrow());
    }

    @Test
    void assemblesASentenceAcrossChunks() {
        SentenceSplitter splitter = new SentenceSplitter(200);
        List<String> out = new ArrayList<>();
        for (String chunk : List.of("The ", "weather is ", "fine today. ", "Bye")) {
            out.addAll(splitter.accept(chunk));
        }

        assertEquals(List.of("The weather is fine today."), out);
        assertEquals("Bye", splitter.flush().orElseThrow());
    }

    @Test
    void cutsAnOverlongRunAtTheLastSpaceSoSpeechNeverWaitsForever() {
        SentenceSplitter splitter = new SentenceSplitter(20);

        List<String> out = splitter.accept("alpha beta gamma delta epsilon zeta");

        assertFalse(out.isEmpty(), "a run longer than the limit is emitted");
        assertTrue(out.stream().allMatch(part -> part.length() <= 20), "no piece exceeds the limit: " + out);
        assertEquals("alpha beta gamma delta epsilon zeta", String.join(" ", out)
            + " " + splitter.flush().orElse("").trim());
    }

    @Test
    void flushOnAnEmptySplitterReturnsNothing() {
        assertTrue(new SentenceSplitter(200).flush().isEmpty());
    }
}
