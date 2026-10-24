package dev.omatheusmesmo.qlawkus.model;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A permanent answer from the primary embedding endpoint must fail loudly rather than reach the
 * fallback. A fallback embedding comes from a different model, so its vector lands in a different
 * space from everything already stored: recall would silently degrade instead of the turn failing.
 *
 * <p>Driven through the real upstream {@code OpenAiEmbeddingModel} against a stub answering 410, the
 * status NVIDIA returns for a retired model, so the exception mapping the guard relies on is the one
 * the provider actually produces rather than one a test constructs.
 */
@QuarkusTest
@ConnectWireMock
class FallbackEmbeddingModelTest {

    private static final String GONE_PATH = "/retired/v1/embeddings";

    WireMock wiremock;

    @ConfigProperty(name = "quarkus.wiremock.devservices.port")
    int wiremockPort;

    @Inject
    EmbeddingGuard guard;

    @Inject
    ModelFallbackConfig config;

    @BeforeEach
    void stubRetiredModel() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING);
        wiremock.resetRequests();
        wiremock.register(WireMock.post(WireMock.urlEqualTo(GONE_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(410)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"model retired\"}")));
    }

    @Test
    void aRetiredModelFailsLoudlyInsteadOfReachingTheFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        FallbackEmbeddingModel model = new FallbackEmbeddingModel(retiredPrimary(), counting(fallbackCalls), guard, config);

        NonRetriableException thrown = assertThrows(NonRetriableException.class, () -> model.embed("query"));

        assertEquals(410, statusOf(thrown), "the provider's status must survive to the caller");
        assertEquals(0, fallbackCalls.get(),
                "a fallback vector from another model would land in another space and degrade recall silently");
        assertEquals(1, wiremock.find(WireMock.postRequestedFor(WireMock.urlEqualTo(GONE_PATH))).size(),
                "a permanent status must not be retried");
        assertEquals(CircuitBreakerState.CLOSED,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_EMBEDDING));
    }

    private EmbeddingModel retiredPrimary() {
        return new PrimaryEmbeddingModelProducer().primaryEmbeddingModel(
                "http://localhost:" + wiremockPort + "/retired/v1",
                "test-key",
                "nvidia/nv-embedqa-e5-v5",
                Optional.empty(),
                Optional.empty());
    }

    private static EmbeddingModel counting(AtomicInteger calls) {
        return new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                calls.incrementAndGet();
                return Response.from(List.of(Embedding.from(new float[] {1f})));
            }
        };
    }

    private static int statusOf(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http) {
                return http.statusCode();
            }
        }
        return -1;
    }
}
