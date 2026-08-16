package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FallbackStreamingChatModel} bridges the callback-based {@code StreamingChatModel} API
 * through {@link PrimaryChatGuard}'s blocking {@code Supplier}, forwarding partials live rather than
 * buffering them until the guard decides the attempt succeeded. These tests exist because that live
 * forwarding creates a rule with real consequences if it regresses: once any token has reached the
 * real caller, a later failure on that same attempt can no longer be retried or silently answered by
 * the Ollama fallback without duplicating or reordering what the user already saw.
 */
@QuarkusTest
class FallbackStreamingChatModelTest {

    @Inject
    PrimaryChatGuard guard;

    @Inject
    ModelFallbackConfig config;

    @BeforeEach
    void resetCircuit() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
    }

    @Test
    void successForwardsPartialsAndCompletionWithoutTouchingFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        StreamingChatModel delegate = scripted((request, handler) -> {
            handler.onPartialResponse("hel");
            handler.onPartialResponse("lo");
            handler.onCompleteResponse(response("hello"));
        });
        StreamingChatModel fallback = failIfInvoked(fallbackCalls);

        FallbackStreamingChatModel model = new FallbackStreamingChatModel(delegate, fallback, guard, config);
        RecordingHandler downstream = new RecordingHandler();

        model.doChat(request(), downstream);

        assertEquals(List.of("hel", "lo"), downstream.partials);
        assertEquals("hello", text(downstream.complete));
        assertNull(downstream.error);
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void failuresBeforeAnyPartialRetryThenFallBackToOllama() {
        AtomicInteger delegateAttempts = new AtomicInteger();
        StreamingChatModel delegate = scripted((request, handler) -> {
            delegateAttempts.incrementAndGet();
            handler.onError(new RuntimeException("primary is down"));
        });
        StreamingChatModel fallback = scripted((request, handler) -> handler.onCompleteResponse(response("from-ollama")));

        FallbackStreamingChatModel model = new FallbackStreamingChatModel(delegate, fallback, guard, config);
        RecordingHandler downstream = new RecordingHandler();

        model.doChat(request(), downstream);

        assertEquals(3, delegateAttempts.get(), "1 initial attempt + 2 retries, per the test-profile config");
        assertEquals("from-ollama", text(downstream.complete));
        assertNull(downstream.error, "the fallback succeeded, so no error should ever reach the caller");
    }

    @Test
    void errorAfterAPartialIsForwardedDirectlyWithoutRetryingOrFallingBack() {
        AtomicInteger delegateAttempts = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        StreamingChatModel delegate = scripted((request, handler) -> {
            delegateAttempts.incrementAndGet();
            handler.onPartialResponse("already visible to the user");
            handler.onError(new RuntimeException("connection dropped mid-stream"));
        });
        StreamingChatModel fallback = failIfInvoked(fallbackCalls);

        FallbackStreamingChatModel model = new FallbackStreamingChatModel(delegate, fallback, guard, config);
        RecordingHandler downstream = new RecordingHandler();

        model.doChat(request(), downstream);

        assertEquals(1, delegateAttempts.get(), "a partial already sent to the user must not be retried");
        assertEquals(0, fallbackCalls.get(), "switching to Ollama now would duplicate or reorder visible tokens");
        assertTrue(downstream.partials.contains("already visible to the user"));
        assertNull(downstream.complete);
        assertFalse(downstream.error == null, "the error must reach the real caller directly");
    }

    /**
     * {@code StreamingChatModel} has no abstract methods (every member, including {@code doChat}, is
     * {@code default}), so it is not a functional interface a lambda can implement directly - this
     * bridges a two-argument script into the one method that matters for these tests.
     */
    private static StreamingChatModel scripted(java.util.function.BiConsumer<ChatRequest, StreamingChatResponseHandler> script) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                script.accept(request, handler);
            }
        };
    }

    private static StreamingChatModel failIfInvoked(AtomicInteger calls) {
        return scripted((request, handler) -> {
            calls.incrementAndGet();
            throw new AssertionError("fallback must not be invoked in this scenario");
        });
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static String text(ChatResponse response) {
        return response == null ? null : response.aiMessage().text();
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {
        final List<String> partials = new ArrayList<>();
        volatile ChatResponse complete;
        volatile Throwable error;

        @Override
        public void onPartialResponse(String partial) {
            partials.add(partial);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            complete = response;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }
    }
}
