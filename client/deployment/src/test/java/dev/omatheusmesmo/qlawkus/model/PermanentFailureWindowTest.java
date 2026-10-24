package dev.omatheusmesmo.qlawkus.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Readiness going DOWN takes a single instance out of rotation, which also cuts off the traffic
 * whose success would clear the streak. The window is what keeps that from being permanent: past it
 * the verdict lapses so a request can probe the provider, and the streak is kept so one more
 * permanent failure is enough to go straight back DOWN.
 */
class PermanentFailureWindowTest {

    private static final int THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Instant LAST_FAILURE = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void aRecentStreakAtTheThresholdBlocks() {
        PermanentFailures.Streak streak = new PermanentFailures.Streak(THRESHOLD, 410, LAST_FAILURE);

        assertTrue(streak.blocks(THRESHOLD, WINDOW, LAST_FAILURE.plusSeconds(60)));
    }

    @Test
    void theVerdictLapsesOnceTheWindowPassesWithoutANewFailure() {
        PermanentFailures.Streak streak = new PermanentFailures.Streak(THRESHOLD, 410, LAST_FAILURE);

        assertFalse(streak.blocks(THRESHOLD, WINDOW, LAST_FAILURE.plus(WINDOW).plusSeconds(1)),
                "an instance held out of rotation forever could never see the success that clears it");
    }

    @Test
    void oneFailureAfterTheLapseBlocksAgainStraightAway() {
        Instant probe = LAST_FAILURE.plus(WINDOW).plusSeconds(1);
        PermanentFailures.Streak streak = new PermanentFailures.Streak(THRESHOLD, 410, LAST_FAILURE)
                .failedWith(410, probe);

        assertTrue(streak.blocks(THRESHOLD, WINDOW, probe),
                "the probe confirmed the provider is still wrong, so it must not take another full run");
    }

    @Test
    void noStreakNeverBlocks() {
        assertFalse(PermanentFailures.Streak.NONE.blocks(THRESHOLD, WINDOW, LAST_FAILURE));
    }
}
