package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link EmbeddingGuard} backs {@link FallbackEmbeddingModel}'s three differently-typed methods
 * through one {@code TypedGuard<Object>}; these tests prove the generic {@link #call} boundary works
 * for both a scalar and a list result, and that a breaker trip on one call type is visible to a call
 * of the other type, matching {@link PrimaryChatGuardTest}'s coverage for the chat side.
 */
@QuarkusTest
class EmbeddingGuardTest {

    @Inject
    EmbeddingGuard guard;

    @BeforeEach
    void resetCircuit() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING);
    }

    @Test
    void primarySucceedsOnFirstAttempt() {
        String result = guard.call(() -> "primary-vector", () -> "fallback-vector");

        assertEquals("primary-vector", result);
    }

    @Test
    void retriesExhaustedFallsBackAndSharesStateWithAnotherResultType() {
        AtomicInteger scalarAttempts = new AtomicInteger();
        Supplier<String> failingScalar = () -> {
            scalarAttempts.incrementAndGet();
            throw new RuntimeException("primary embeddings are down");
        };

        String scalarResult = guard.call(failingScalar, () -> "fallback-vector");
        assertEquals("fallback-vector", scalarResult);
        assertEquals(3, scalarAttempts.get(), "1 initial attempt + 2 retries, per the test-profile config");
        assertEquals(CircuitBreakerState.OPEN,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING));

        AtomicInteger listAttempts = new AtomicInteger();
        Supplier<java.util.List<String>> failingList = () -> {
            listAttempts.incrementAndGet();
            throw new RuntimeException("still down");
        };

        java.util.List<String> listResult = guard.call(failingList, () -> java.util.List.of("fallback-list"));

        assertEquals(java.util.List.of("fallback-list"), listResult);
        assertEquals(0, listAttempts.get(),
                "the breaker embed() tripped must reject embedAll() before it reaches the primary supplier");
    }
}
