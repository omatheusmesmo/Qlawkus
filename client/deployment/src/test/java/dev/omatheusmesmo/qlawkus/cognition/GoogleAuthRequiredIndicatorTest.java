package dev.omatheusmesmo.qlawkus.cognition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthRequiredIndicatorTest {

    private final GoogleAuthRequiredIndicator indicator = new GoogleAuthRequiredIndicator();

    @AfterEach
    void tearDown() {
        indicator.reset();
    }

    @Test
    void isAuthRequired_defaultIsFalse() {
        assertFalse(indicator.isAuthRequired());
    }

    @Test
    void markAuthRequired_setsToTrue() {
        indicator.markAuthRequired();
        assertTrue(indicator.isAuthRequired());
    }

    @Test
    void reset_clearsFlag() {
        indicator.markAuthRequired();
        assertTrue(indicator.isAuthRequired());

        indicator.reset();
        assertFalse(indicator.isAuthRequired());
    }

    @Test
    void markAuthRequired_multipleTimesStaysTrue() {
        indicator.markAuthRequired();
        indicator.markAuthRequired();
        assertTrue(indicator.isAuthRequired());
    }

    @Test
    void reset_whenNotSet_isIdempotent() {
        indicator.reset();
        assertFalse(indicator.isAuthRequired());
    }

    @Test
    void markAndReset_threadIsolation() throws Exception {
        indicator.markAuthRequired();

        Thread other = new Thread(() -> {
            assertFalse(indicator.isAuthRequired());
            indicator.markAuthRequired();
            assertTrue(indicator.isAuthRequired());
            indicator.reset();
            assertFalse(indicator.isAuthRequired());
        });

        other.start();
        other.join();

        assertTrue(indicator.isAuthRequired());
    }
}
