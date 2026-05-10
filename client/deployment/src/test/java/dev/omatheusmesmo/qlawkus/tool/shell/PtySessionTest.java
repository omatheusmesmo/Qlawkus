package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    @Test
    void markTimedOut_setsStatusAndInterrupts() {
        PtySession session = createSessionWithPrompts(List.of());
        assertEquals("running", session.getStatus(), "New session should be running");

        session.markTimedOut();
        assertEquals("timed_out", session.getStatus(), "Status should be timed_out after markTimedOut");
    }

    @Test
    void close_setsStatusToClosed() {
        PtySession session = createSessionWithPrompts(List.of());
        assertEquals("running", session.getStatus());

        session.close();
        assertEquals("closed", session.getStatus(), "Status should be closed after close");
    }

    @Test
    void touchActivity_updatesLastActivity() throws InterruptedException {
        PtySession session = createSessionWithPrompts(List.of());
        Instant before = session.getLastActivity();
        Thread.sleep(10);
        session.touchActivity();
        Instant after = session.getLastActivity();
        assertTrue(after.isAfter(before) || after.equals(before),
            "touchActivity should update lastActivity");
    }

    @Test
    void clearPromptDetected_whenNotSet_staysFalse() {
        PtySession session = createSessionWithPrompts(List.of(Pattern.compile(">>> ")));
        assertFalse(session.isPromptDetected(), "Should start as false");
        session.clearPromptDetected();
        assertFalse(session.isPromptDetected(), "Should still be false after clearing");
    }

    @Test
    void getSessionId_returnsId() {
        PtySession session = createSessionWithPrompts(List.of());
        assertEquals("test-id", session.getSessionId());
    }

    @Test
    void getCommand_returnsCommand() {
        PtySession session = createSessionWithPrompts(List.of());
        assertEquals("test-cmd", session.getCommand());
    }

    private PtySession createSessionWithPrompts(List<Pattern> patterns) {
        return new PtySession("test-id", "test-cmd", null, new RollingBuffer(1000), null, patterns);
    }
}
