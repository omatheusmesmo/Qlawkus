package dev.omatheusmesmo.qlawkus.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class FailureSummaryTest {

    private static final UnaryOperator<String> NO_REDACTION = text -> text;
    private static final String SECRET = "sk-live-123456789";

    @Test
    void theErrorIsTheDeepestCauseNotTheWrapperThatSurfacedIt() {
        Throwable failure = new IllegalStateException(
                new InvocationTargetException(new RuntimeException("exceeded 10 sequential tool executions")));

        FailureSummary summary = FailureSummary.of(failure, NO_REDACTION);

        assertEquals("RuntimeException", summary.error());
        assertEquals("exceeded 10 sequential tool executions", summary.cause());
    }

    @Test
    void aFailureWithNoCauseIsItsOwnRoot() {
        FailureSummary summary = FailureSummary.of(new IllegalArgumentException("bad id"), NO_REDACTION);

        assertEquals("IllegalArgumentException", summary.error());
        assertEquals("bad id", summary.cause());
    }

    @Test
    void aRootWithoutAMessageSaysSo() {
        FailureSummary summary = FailureSummary.of(new NullPointerException(), NO_REDACTION);

        assertEquals("NullPointerException", summary.error());
        assertEquals("(no message)", summary.cause());
    }

    @Test
    void aMultiLineMessageBecomesOneLine() {
        String line = FailureSummary.boundedLine("first line\r\n  second\tline\n", NO_REDACTION);

        assertEquals("first line second line", line);
    }

    @Test
    void aLongMessageIsCappedAtTwoHundredCharacters() {
        String line = FailureSummary.boundedLine("x".repeat(500), NO_REDACTION);

        assertEquals(200, line.length());
        assertTrue(line.endsWith("..."), line);
    }

    @Test
    void theSecretIsRedactedBeforeTheCapCutsTheText() {
        String message = "a".repeat(190) + " " + SECRET + " " + "b".repeat(100);

        String line = FailureSummary.boundedLine(message, text -> text.replace(SECRET, "[redacted]"));

        assertEquals(200, line.length());
        assertFalse(line.contains("sk-live"), line);
    }

    @Test
    void theRedactorSeesTheRootMessageNotTheWrapperMessage() {
        Throwable failure = new IllegalStateException("wrapper " + SECRET,
                new RuntimeException("root " + SECRET));

        FailureSummary summary = FailureSummary.of(failure, text -> text.replace(SECRET, "[redacted]"));

        assertEquals("root [redacted]", summary.cause());
    }

    @Test
    void aCyclicChainTerminates() {
        Exception first = new Exception("first");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);

        FailureSummary summary = FailureSummary.of(first, NO_REDACTION);

        assertEquals("Exception", summary.error());
        assertTrue(summary.cause().equals("first") || summary.cause().equals("second"), summary.toString());
    }

    @Test
    void firstFindsTheNearestMatchingLinkInTheChain() {
        Throwable failure = new IllegalStateException("outer",
                new IllegalArgumentException("inner"));

        Optional<IllegalArgumentException> found = FailureChain.first(failure, IllegalArgumentException.class);

        assertEquals("inner", found.orElseThrow().getMessage());
        assertFalse(FailureChain.first(failure, UnsupportedOperationException.class).isPresent());
    }
}
