package dev.omatheusmesmo.qlawkus.routine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoutinePromptsTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 27, 16, 0, 0, 0,
            ZoneId.of("America/Sao_Paulo"));

    @Test
    void theWeeklyPromptAsksForTheWeekFromEveryAvailableSourceAndForbidsWritingToBrag() {
        String prompt = RoutinePrompts.build(Routine.WEEKLY, NOW, Optional.empty(), 14);

        assertTrue(prompt.contains("2026-11-27"), prompt);
        assertTrue(prompt.contains("gh"), prompt);
        assertTrue(prompt.contains("brag"), prompt);
        assertTrue(prompt.contains("Do not record anything"), prompt);
        assertTrue(prompt.contains("say plainly what you could not read"), prompt);
    }

    @Test
    void theMorningPromptCarriesTheAbandonedThreshold() {
        String prompt = RoutinePrompts.build(Routine.MORNING, NOW, Optional.empty(), 9);

        assertTrue(prompt.contains("Google Calendar"), prompt);
        assertTrue(prompt.contains("9 days"), prompt);
        assertTrue(prompt.contains("say plainly what you could not read"), prompt);
    }

    @Test
    void anOwnerPromptReplacesTheDefaultTextButKeepsTheContextAndTheReplyContract() {
        String prompt = RoutinePrompts.build(Routine.MORNING, NOW, Optional.of("Only list the standup."), 9);

        assertTrue(prompt.contains("Only list the standup."), prompt);
        assertFalse(prompt.contains("look abandoned"), "the default instruction is replaced, not appended: " + prompt);
        assertTrue(prompt.contains("2026-11-27"), prompt);
        assertTrue(prompt.contains("9 days"), "the threshold is still stated so a custom prompt can use it: " + prompt);
        assertTrue(prompt.contains("say plainly what you could not read"), prompt);
    }

    @Test
    void aBlankOwnerPromptFallsBackToTheDefault() {
        String prompt = RoutinePrompts.build(Routine.WEEKLY, NOW, Optional.of("   "), 14);

        assertTrue(prompt.contains("brag"), prompt);
    }
}
