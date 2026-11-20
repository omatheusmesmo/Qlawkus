package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceSessionLimiterTest {

    @Test
    void anOwnerIsCappedAtTheConfiguredNumberOfConcurrentSessions() {
        VoiceSessionLimiter limiter = new VoiceSessionLimiter(1);

        assertTrue(limiter.tryAcquire("owner"));
        assertFalse(limiter.tryAcquire("owner"), "a second session for the same owner is refused");
    }

    @Test
    void releasingASessionFreesTheSlot() {
        VoiceSessionLimiter limiter = new VoiceSessionLimiter(1);
        limiter.tryAcquire("owner");

        limiter.release("owner");

        assertTrue(limiter.tryAcquire("owner"));
    }

    @Test
    void ownersAreCountedSeparately() {
        VoiceSessionLimiter limiter = new VoiceSessionLimiter(1);

        assertTrue(limiter.tryAcquire("first"));
        assertTrue(limiter.tryAcquire("second"));
    }

    @Test
    void releasingMoreThanAcquiredNeverGoesNegative() {
        VoiceSessionLimiter limiter = new VoiceSessionLimiter(1);
        limiter.release("owner");

        assertTrue(limiter.tryAcquire("owner"));
        assertFalse(limiter.tryAcquire("owner"));
    }
}
