package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.model.chat.response.ChatResponse;
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
 * Exercises the retry + circuit breaker + fallback pipeline directly against plain suppliers, without
 * a real {@code ChatModel} or network stub - the policy under test lives entirely in this class, and
 * {@link FallbackChatModel}/{@link FallbackStreamingChatModel} are thin callers of it.
 *
 * <p>{@link PrimaryChatGuard} is {@code @ApplicationScoped} and {@code @Startup}: one instance, one
 * registered circuit breaker, shared by every test method in this class (and by any other test class
 * that boots the same app). The breaker is reset before each test so failures from one test cannot
 * leave the next one starting HALF_OPEN or OPEN.
 */
@QuarkusTest
class PrimaryChatGuardTest {

    @Inject
    PrimaryChatGuard guard;

    @BeforeEach
    void resetCircuit() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
    }

    @Test
    void primarySucceedsOnFirstAttemptAndFallbackIsNeverConsulted() {
        AtomicInteger fallbackCalls = new AtomicInteger();

        ChatResponse response = guard.call(
                () -> response("primary"),
                () -> {
                    fallbackCalls.incrementAndGet();
                    return response("fallback");
                });

        assertEquals("primary", text(response));
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void primarySucceedsAfterOneRetry() {
        AtomicInteger attempts = new AtomicInteger();

        ChatResponse response = guard.call(
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new RuntimeException("transient blip");
                    }
                    return response("primary");
                },
                () -> response("fallback"));

        assertEquals("primary", text(response));
        assertEquals(2, attempts.get(), "must have retried exactly once before succeeding");
    }

    @Test
    void retriesExhaustedFallsBackToOllama() {
        AtomicInteger primaryAttempts = new AtomicInteger();

        ChatResponse response = guard.call(
                () -> {
                    primaryAttempts.incrementAndGet();
                    throw new RuntimeException("primary is down");
                },
                () -> response("fallback"));

        assertEquals("fallback", text(response));
        assertEquals(3, primaryAttempts.get(), "1 initial attempt + 2 retries, per the test-profile config");
    }

    @Test
    void nonRetryableExceptionSkipsBothRetryAndFallback() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        NonRetriableException authError = new NonRetriableException("bad api key");

        NonRetriableException thrown = assertThrows(NonRetriableException.class, () -> guard.call(
                () -> {
                    primaryAttempts.incrementAndGet();
                    throw authError;
                },
                () -> {
                    fallbackCalls.incrementAndGet();
                    return response("fallback");
                }));

        assertSame(authError, thrown, "the original exception must propagate unwrapped");
        assertEquals(1, primaryAttempts.get(), "an auth/config error must not be retried");
        assertEquals(0, fallbackCalls.get(), "an auth/config error must not be masked by a silent fallback");
    }

    @Test
    void openCircuitShortCircuitsStraightToFallbackWithoutCallingPrimaryAgain() {
        AtomicInteger primaryAttempts = new AtomicInteger();
        Supplier<ChatResponse> failingPrimary = () -> {
            primaryAttempts.incrementAndGet();
            throw new RuntimeException("primary is down");
        };

        // One fully-retried call already contributes 3 failed attempts (1 + 2 retries) to the
        // window, which is exactly the configured requestVolumeThreshold - this call trips it.
        guard.call(failingPrimary, () -> response("fallback"));
        assertEquals(CircuitBreakerState.OPEN,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT));

        int attemptsBeforeSecondCall = primaryAttempts.get();
        ChatResponse response = guard.call(failingPrimary, () -> response("fallback"));

        assertEquals("fallback", text(response));
        assertEquals(attemptsBeforeSecondCall, primaryAttempts.get(),
                "an open circuit must reject the call before it ever reaches the primary supplier");
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }

    private static String text(ChatResponse response) {
        return response.aiMessage().text();
    }
}
