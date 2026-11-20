package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Pins the category silenced in the core's {@code META-INF/microprofile-config.properties}: langchain4j
 * warns "Improper invocation of a standalone agent" on every messaging turn, which is not a fault here.
 * Only that category is quieted, so its errors and every other logger keep their level.
 */
@QuarkusTest
class AgentInvocationHandlerLogLevelTest {

    private static final String NOISY_CATEGORY = "dev.langchain4j.agentic.agent.AgentInvocationHandler";

    @Test
    void theStandaloneAgentWarningIsSilenced() {
        assertFalse(Logger.getLogger(NOISY_CATEGORY).isLoggable(Level.WARNING));
    }

    @Test
    void errorsFromThatCategoryStillShowAndOtherWarningsAreUntouched() {
        assertTrue(Logger.getLogger(NOISY_CATEGORY).isLoggable(Level.SEVERE));
        assertTrue(Logger.getLogger("dev.omatheusmesmo.qlawkus.agent").isLoggable(Level.WARNING));
    }
}
