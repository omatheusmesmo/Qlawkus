package dev.omatheusmesmo.qlawkus.tool.quarantine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolQuarantineTest {

    private final List<ToolQuarantinedEvent> fired = new ArrayList<>();
    private MutableClock clock;
    private boolean enabled;
    private ToolQuarantine quarantine;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-26T12:00:00Z"));
        enabled = true;
        quarantine = new ToolQuarantine(new ToolQuarantineConfig() {
            public boolean enabled() {
                return enabled;
            }

            public int failureThreshold() {
                return 3;
            }

            public Duration cooldown() {
                return Duration.ofMinutes(10);
            }
        }, clock, fired::add);
    }

    @Test
    void aToolStaysAvailableUntilItFailsInARowAtTheThreshold() {
        quarantine.recordFailure("readFile");
        quarantine.recordFailure("readFile");

        assertFalse(quarantine.isQuarantined("readFile"));
        assertTrue(fired.isEmpty());

        quarantine.recordFailure("readFile");

        assertTrue(quarantine.isQuarantined("readFile"));
        assertEquals(1, fired.size());
        assertEquals("readFile", fired.getFirst().tool());
        assertEquals(clock.instant().plus(Duration.ofMinutes(10)), fired.getFirst().until());
    }

    @Test
    void aSuccessBreaksTheStreakSoOnlyConsecutiveFailuresCount() {
        quarantine.recordFailure("readFile");
        quarantine.recordFailure("readFile");
        quarantine.recordSuccess("readFile");
        quarantine.recordFailure("readFile");
        quarantine.recordFailure("readFile");

        assertFalse(quarantine.isQuarantined("readFile"));
    }

    @Test
    void toolsAreCountedIndependently() {
        quarantine.recordFailure("readFile");
        quarantine.recordFailure("readFile");
        quarantine.recordFailure("fetchPage");

        assertFalse(quarantine.isQuarantined("readFile"));
        assertFalse(quarantine.isQuarantined("fetchPage"));
    }

    @Test
    void theToolComesBackAfterTheCooldownAndOneMoreFailureQuarantinesItAgain() {
        trip("fetchPage");
        clock.advance(Duration.ofMinutes(10));

        assertFalse(quarantine.isQuarantined("fetchPage"));

        quarantine.recordFailure("fetchPage");

        assertTrue(quarantine.isQuarantined("fetchPage"), "a probe that fails again must not get a fresh streak");
        assertEquals(2, fired.size());
    }

    @Test
    void aSuccessfulProbeAfterTheCooldownClearsTheState() {
        trip("fetchPage");
        clock.advance(Duration.ofMinutes(10));
        quarantine.recordSuccess("fetchPage");
        quarantine.recordFailure("fetchPage");

        assertFalse(quarantine.isQuarantined("fetchPage"));
        assertTrue(quarantine.quarantined().isEmpty());
    }

    @Test
    void releaseReturnsTheToolWithAFreshStreak() {
        trip("fetchPage");

        assertTrue(quarantine.release("fetchPage"));
        assertFalse(quarantine.isQuarantined("fetchPage"));
        assertFalse(quarantine.release("fetchPage"), "nothing left to release");

        quarantine.recordFailure("fetchPage");
        assertFalse(quarantine.isQuarantined("fetchPage"));
    }

    @Test
    void failuresWhileQuarantinedDoNotExtendTheWindowOrFireAgain() {
        trip("fetchPage");
        clock.advance(Duration.ofMinutes(4));
        quarantine.recordFailure("fetchPage");

        assertEquals(1, fired.size());
        assertEquals(1, quarantine.quarantined().size());
        assertEquals(Instant.parse("2026-11-26T12:10:00Z"), quarantine.quarantined().getFirst().until());
    }

    @Test
    void nothingIsQuarantinedWhenDisabled() {
        enabled = false;
        trip("fetchPage");

        assertFalse(quarantine.isQuarantined("fetchPage"));
        assertTrue(fired.isEmpty());
    }

    private void trip(String tool) {
        for (int i = 0; i < 3; i++) {
            quarantine.recordFailure(tool);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
