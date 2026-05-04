package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PtySessionTest {

    @Test
    void checkPrompt_matchingPattern_setsPromptDetected() {
        PtySession session = createSessionWithPrompts(List.of(Pattern.compile("[#$>] ")));

        session.checkPrompt("user@host:~$ ");
        assertTrue(session.isPromptDetected(), "Should detect bash prompt pattern");
    }

    @Test
    void checkPrompt_pythonRepl_setsPromptDetected() {
        PtySession session = createSessionWithPrompts(List.of(Pattern.compile(">>> ")));

        session.checkPrompt(">>> ");
        assertTrue(session.isPromptDetected(), "Should detect Python REPL prompt");
    }

    @Test
    void checkPrompt_noMatch_doesNotSetPromptDetected() {
        PtySession session = createSessionWithPrompts(List.of(Pattern.compile(">>> ")));

        session.checkPrompt("some output line");
        assertFalse(session.isPromptDetected(), "Should not detect prompt in regular output");
    }

    @Test
    void checkPrompt_emptyPatterns_doesNotSetPromptDetected() {
        PtySession session = createSessionWithPrompts(List.of());

        session.checkPrompt("$ ");
        assertFalse(session.isPromptDetected(), "No patterns means no detection");
    }

    @Test
    void clearPromptDetected_resetsFlag() {
        PtySession session = createSessionWithPrompts(List.of(Pattern.compile("[#$>] ")));

        session.checkPrompt("$ ");
        assertTrue(session.isPromptDetected());

        session.clearPromptDetected();
        assertFalse(session.isPromptDetected(), "Should reset prompt detected flag");
    }

    @Test
    void getPromptPatternStrings_returnsPatternStrings() {
        PtySession session = createSessionWithPrompts(
                List.of(Pattern.compile("[#$>] "), Pattern.compile(">>> ")));

        List<String> patterns = session.getPromptPatternStrings();
        assertEquals(2, patterns.size());
        assertTrue(patterns.contains("[#$>] "));
        assertTrue(patterns.contains(">>> "));
    }

    @Test
    void checkPrompt_multiplePatterns_matchesAny() {
        PtySession session = createSessionWithPrompts(
                List.of(Pattern.compile("[#$>] "), Pattern.compile(">>> ")));

        session.checkPrompt(">>> import os");
        assertTrue(session.isPromptDetected(), "Should match Python pattern");
    }

    private PtySession createSessionWithPrompts(List<Pattern> patterns) {
        return new PtySession("test-id", "test-cmd", null, new RollingBuffer(1000), null, patterns);
    }
}
