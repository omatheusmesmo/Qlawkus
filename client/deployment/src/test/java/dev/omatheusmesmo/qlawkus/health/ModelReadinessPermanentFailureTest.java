package dev.omatheusmesmo.qlawkus.health;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.omatheusmesmo.qlawkus.model.EmbeddingGuard;
import dev.omatheusmesmo.qlawkus.model.FallbackChatModel;
import dev.omatheusmesmo.qlawkus.model.FallbackEmbeddingModel;
import dev.omatheusmesmo.qlawkus.model.SecondaryChatGuard;
import dev.omatheusmesmo.qlawkus.model.FallbackStreamingChatModel;
import dev.omatheusmesmo.qlawkus.model.ModelFallbackConfig;
import dev.omatheusmesmo.qlawkus.model.PrimaryChatGuard;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A permanent answer from the primary provider (a retired model, a revoked key, a wrong model name)
 * never opens a breaker and never reaches the fallback, so before this check a provider that failed
 * every turn left readiness UP. These tests pin the signal that replaced that blind spot: consecutive
 * permanent failures on the interactive workload take the check DOWN, naming the status, and a single
 * success clears it.
 *
 * <p>The test profile keeps the default threshold of 3.
 */
@QuarkusTest
class ModelReadinessPermanentFailureTest {

    private static final int THRESHOLD = 3;

    @Inject
    @Readiness
    ModelReadinessCheck readiness;

    @Inject
    PrimaryChatGuard chatGuard;

    @Inject
    SecondaryChatGuard secondaryGuard;

    @Inject
    EmbeddingGuard embeddingGuard;

    @BeforeEach
    void clearOutcomes() {
        CircuitBreakerMaintenance.get().resetAll();
        for (String workload : List.of(WorkloadContext.INTERACTIVE, WorkloadContext.BATCH)) {
            WorkloadContext.runAs(workload, () -> {
                chatGuard.call(() -> response("ok"), () -> response("fallback"));
                embeddingGuard.call(() -> "vector", () -> "fallback-vector");
            });
        }
    }

    @Test
    void consecutivePermanentChatFailuresTakeReadinessDown() {
        failChat(THRESHOLD, permanent(410));

        HealthCheckResponse response = readiness.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus(),
                "a primary that answers 410 on every turn cannot serve, whatever its breaker says");
        assertEquals("CLOSED", data(response).get("chatCircuit"),
                "the signal must not depend on the breaker, which a permanent error never opens");
        assertEquals(3L, data(response).get("chatPermanentFailures"));
        assertTrue(String.valueOf(data(response).get("chatError")).contains("410"),
                "the check must name the status so the operator knows what to fix: " + data(response));
    }

    @Test
    void fewerFailuresThanTheThresholdStayUp() {
        failChat(THRESHOLD - 1, permanent(410));

        HealthCheckResponse response = readiness.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(2L, data(response).get("chatPermanentFailures"));
    }

    @Test
    void oneSuccessResetsTheStreak() {
        failChat(THRESHOLD, permanent(401));

        chatGuard.call(() -> response("recovered"), () -> response("fallback"));

        HealthCheckResponse response = readiness.call();
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(0L, data(response).get("chatPermanentFailures"));
        assertFalse(data(response).containsKey("chatError"));
    }

    @Test
    void aTransientFailureNeitherCountsNorResetsTheStreak() {
        failChat(THRESHOLD - 1, permanent(404));
        chatGuard.call(() -> {
            throw new RuntimeException("provider blip");
        }, () -> response("fallback"));
        CircuitBreakerMaintenance.get().resetAll();
        failChat(1, permanent(404));

        assertEquals(HealthCheckResponse.Status.DOWN, readiness.call().getStatus(),
                "a transient failure is no evidence the key or the model came back");
    }

    @Test
    void aBadRequestIsAboutTheRequestNotTheProvider() {
        failChat(THRESHOLD, permanent(400));

        HealthCheckResponse response = readiness.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "a 400 (an oversized context, a malformed tool schema) fails one request, not every turn");
        assertEquals(0L, data(response).get("chatPermanentFailures"));
    }

    @Test
    void batchPermanentFailuresAreReportedButNeverFlipTheStatus() {
        WorkloadContext.runAs(WorkloadContext.BATCH, () -> failChat(THRESHOLD, permanent(410)));

        HealthCheckResponse response = readiness.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                "a background job must not take the instance out of rotation");
        assertEquals(3L, data(response).get("batchChatPermanentFailures"));
        assertEquals(0L, data(response).get("chatPermanentFailures"));
    }

    @Test
    void consecutivePermanentEmbeddingFailuresTakeReadinessDown() {
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(AuthenticationException.class, () -> embeddingGuard.call(() -> {
                throw new AuthenticationException(new HttpException(401, "revoked key"));
            }, () -> "fallback-vector"));
        }

        HealthCheckResponse response = readiness.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus(),
                "every turn embeds the query first, so a dead embedding endpoint fails every turn");
        assertEquals(3L, data(response).get("embeddingPermanentFailures"));
        assertTrue(String.valueOf(data(response).get("embeddingError")).contains("401"));
    }

    @Test
    void withFallbackDisabledTheChatPathStillFeedsReadiness() {
        ChatModel primary = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw permanent(410);
            }
        };
        FallbackChatModel model = new FallbackChatModel(primary, primary, primary, chatGuard, secondaryGuard, fallbackDisabled());

        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(InvalidRequestException.class, () -> model.chat(request()));
        }

        assertEquals(HealthCheckResponse.Status.DOWN, readiness.call().getStatus());
    }

    @Test
    void withFallbackDisabledTheStreamingPathStillFeedsReadiness() {
        StreamingChatModel primary = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onError(permanent(410));
            }
        };
        FallbackStreamingChatModel model =
                new FallbackStreamingChatModel(primary, primary, primary, chatGuard, secondaryGuard, fallbackDisabled());

        for (int i = 0; i < THRESHOLD; i++) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            model.chat(request(), new StreamingChatResponseHandler() {
                @Override
                public void onCompleteResponse(ChatResponse response) {
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                }
            });
            assertTrue(error.get() instanceof InvalidRequestException,
                    "the caller must still receive the provider's error, got " + error.get());
        }

        assertEquals(HealthCheckResponse.Status.DOWN, readiness.call().getStatus());
    }

    @Test
    void withFallbackDisabledTheEmbeddingPathStillFeedsReadiness() {
        EmbeddingModel primary = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                throw new ModelNotFoundException(new HttpException(404, "no such model"));
            }
        };
        FallbackEmbeddingModel model = new FallbackEmbeddingModel(primary, primary, embeddingGuard, fallbackDisabled());

        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ModelNotFoundException.class, () -> model.embed("query"));
        }

        assertEquals(HealthCheckResponse.Status.DOWN, readiness.call().getStatus());
    }

    private void failChat(int times, RuntimeException failure) {
        for (int i = 0; i < times; i++) {
            assertThrows(failure.getClass(), () -> chatGuard.call(() -> {
                throw failure;
            }, () -> response("fallback")));
        }
    }

    private static InvalidRequestException permanent(int status) {
        return new InvalidRequestException(new HttpException(status, "provider said " + status));
    }

    private static ModelFallbackConfig fallbackDisabled() {
        ModelFallbackConfig config = Mockito.mock(ModelFallbackConfig.class);
        Mockito.when(config.fallbackEnabled()).thenReturn(false);
        return config;
    }

    private static Map<String, Object> data(HealthCheckResponse response) {
        return response.getData().orElseThrow();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
