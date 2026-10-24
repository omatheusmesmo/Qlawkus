package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * A permanent answer must not reach the fallback: a fallback vector comes from another model and
     * lands in another vector space, so recall would degrade silently instead of the turn failing
     * loudly.
     */
    @Test
    void permanentFailureSkipsBothRetryAndFallback() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        InvalidRequestException retired = new InvalidRequestException(new HttpException(410, "model retired"));

        InvalidRequestException thrown = assertThrows(InvalidRequestException.class, () -> guard.call(
                () -> {
                    primaryAttempts.incrementAndGet();
                    throw retired;
                },
                () -> {
                    fallbackCalls.incrementAndGet();
                    return "fallback-vector";
                }));

        assertSame(retired, thrown);
        assertEquals(1, primaryAttempts.get(), "a permanent status cannot heal on retry");
        assertEquals(0, fallbackCalls.get(), "a fallback vector would land in another vector space");
        assertEquals(CircuitBreakerState.CLOSED,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING));
    }
}
