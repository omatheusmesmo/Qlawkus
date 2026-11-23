package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The approval gate is opt-in. With no configuration every tool keeps its autonomous behaviour, so
 * an upgrade changes nothing until the owner names a tool.
 */
@QuarkusTest
class ApprovalConfigDefaultsTest {

    @Inject
    ApprovalConfig config;

    @Test
    void noToolRequiresApprovalUnlessTheOwnerNamesIt() {
        assertTrue(config.tools().isEmpty());
    }

    @Test
    void theDefaultsAreBoundedAndDocumented() {
        assertEquals(Duration.ofHours(24), config.expiry());
        assertEquals(50, config.maxPending());
        assertEquals(500, config.previewMaxChars());
    }
}
