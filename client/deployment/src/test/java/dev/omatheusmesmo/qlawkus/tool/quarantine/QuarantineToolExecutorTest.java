package dev.omatheusmesmo.qlawkus.tool.quarantine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuarantineToolExecutorTest {

    private static final ToolExecutionRequest REQUEST = ToolExecutionRequest.builder().name("fetchPage")
            .arguments("{}").build();

    private final ToolQuarantine quarantine = new ToolQuarantine(new ToolQuarantineConfig() {
        public boolean enabled() {
            return true;
        }

        public int failureThreshold() {
            return 2;
        }

        public Duration cooldown() {
            return Duration.ofMinutes(5);
        }
    }, Clock.systemUTC(), event -> {
    });

    @Test
    void aFailureOfTheToolItselfCountsAndIsRethrown() {
        ToolExecutor failing = new Stub(() -> {
            throw new UncheckedIOException(new IOException("connection reset"));
        });
        QuarantineToolExecutor executor = new QuarantineToolExecutor("fetchPage", failing, quarantine);

        assertThrows(UncheckedIOException.class, () -> executor.execute(REQUEST, "m"));
        assertThrows(UncheckedIOException.class, () -> executor.executeWithContext(REQUEST, null));

        assertTrue(quarantine.isQuarantined("fetchPage"));
    }

    @Test
    void anErrorResultCountsAsAFailure() {
        ToolExecutor errored = new Stub(() -> ToolExecutionResult.builder().isError(true).resultText("boom").build());
        QuarantineToolExecutor executor = new QuarantineToolExecutor("fetchPage", errored, quarantine);

        executor.executeWithContext(REQUEST, null);
        executor.executeWithContext(REQUEST, null);

        assertTrue(quarantine.isQuarantined("fetchPage"));
    }

    @Test
    void aBadCallFromTheModelNeverCounts() {
        for (RuntimeException badInput : new RuntimeException[] { new IllegalArgumentException("no such path"),
                new ToolArgumentsException("not json"), new ConstraintViolationException(Set.of()),
                new RuntimeException("wrapped", new IllegalArgumentException("bad")) }) {
            ToolExecutor misused = new Stub(() -> {
                throw badInput;
            });
            QuarantineToolExecutor executor = new QuarantineToolExecutor("fetchPage", misused, quarantine);

            assertThrows(RuntimeException.class, () -> executor.execute(REQUEST, "m"));
            assertThrows(RuntimeException.class, () -> executor.execute(REQUEST, "m"));
        }

        assertFalse(quarantine.isQuarantined("fetchPage"));
    }

    @Test
    void aSuccessfulCallPassesTheResultThroughAndResetsTheStreak() {
        boolean[] fail = { true };
        ToolExecutor flaky = new Stub(() -> {
            if (fail[0]) {
                throw new UncheckedIOException(new IOException("down"));
            }
            return "ok";
        });
        QuarantineToolExecutor executor = new QuarantineToolExecutor("fetchPage", flaky, quarantine);

        assertThrows(UncheckedIOException.class, () -> executor.execute(REQUEST, "m"));
        fail[0] = false;
        assertEquals("ok", executor.execute(REQUEST, "m"));
        fail[0] = true;
        assertThrows(UncheckedIOException.class, () -> executor.execute(REQUEST, "m"));

        assertFalse(quarantine.isQuarantined("fetchPage"));
    }

    private static final class Stub implements ToolExecutor {
        private final java.util.function.Supplier<Object> body;

        Stub(java.util.function.Supplier<Object> body) {
            this.body = body;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            return (String) body.get();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            Object result = body.get();
            return result instanceof ToolExecutionResult r ? r
                    : ToolExecutionResult.builder().resultText(String.valueOf(result)).build();
        }
    }

    @Test
    void aCauseChainThatLoopsBackStillEnds() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertFalse(QuarantineToolExecutor.misuse(first));
    }
}
