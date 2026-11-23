package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ApprovedExecutionTest {

    @Test
    void nothingIsApprovedUntilTheServiceSaysSo() {
        assertFalse(ApprovedExecution.active());
    }

    @Test
    void theMarkIsSetInsideTheCallAndGoneAfterIt() {
        boolean inside = ApprovedExecution.run(ApprovedExecution::active);

        assertTrue(inside);
        assertFalse(ApprovedExecution.active());
    }

    @Test
    void theMarkIsClearedWhenTheCallThrows() {
        assertThrows(IllegalStateException.class, () -> ApprovedExecution.run(() -> {
            throw new IllegalStateException("boom");
        }));

        assertFalse(ApprovedExecution.active());
    }

    @Test
    void theMarkDoesNotFollowTheCallOntoAnotherThread() throws Exception {
        AtomicBoolean seenElsewhere = new AtomicBoolean(true);

        ApprovedExecution.run(() -> {
            Thread other = new Thread(() -> seenElsewhere.set(ApprovedExecution.active()));
            other.start();
            try {
                other.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        assertFalse(seenElsewhere.get());
    }

    @Test
    void aNestedCallRestoresTheOuterMarkNotTheDefault() {
        String outcome = ApprovedExecution.run(() -> {
            ApprovedExecution.run(() -> null);
            return ApprovedExecution.active() ? "still-approved" : "lost";
        });

        assertEquals("still-approved", outcome);
    }
}
