package dev.omatheusmesmo.qlawkus.it.markdown;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the half of the telemetry that no upstream extension can emit, and that therefore only
 * exists because Qlawkus writes it: what the cognition subsystem did this turn.
 *
 * <p>{@code TelemetryTest} next to this one pins what arrives from a registry being present. These
 * assertions are the complement, and the reason they matter is that the question they answer, "did
 * memory work this turn?", previously had no answer outside a log line.
 */
@QuarkusTest
@ConnectWireMock
class CognitionTelemetryTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @BeforeEach
    void stubTheModel() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    /**
     * Active memory runs before every reply, so one turn is enough to prove the retriever is being
     * observed. Without this counter a retrieval that silently returned nothing is indistinguishable
     * from one that never ran.
     */
    @Test
    void retrievalIsCountedAndTimed() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("qlawkus_retrieval_queries_total"),
                "retrieval must be counted; scrape was:\n" + metrics);
        assertTrue(metrics.contains("qlawkus_retrieval_duration_seconds"),
                "retrieval must be timed, since it runs on the critical path of every reply");
    }

    /** A retrieval that found nothing is a real outcome, not a missing measurement. */
    @Test
    void retrievalSeparatesHitsFromMisses() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("outcome=\"hit\"") || metrics.contains("outcome=\"miss\""),
                "retrieval must report an outcome tag; scrape was:\n" + metrics);
    }

    /**
     * Store operations carry the backend that served them, which is what gives the
     * markdown/pgvector/hybrid switch a measurable meaning rather than a felt one.
     */
    @Test
    void storeOperationsAreTaggedWithTheBackendThatServedThem() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("qlawkus_store_operations_total"),
                "store operations must be counted; scrape was:\n" + metrics);
        assertTrue(metrics.contains("backend=\"markdown\""),
                "this distribution is markdown-only, so its store meters must say so");
    }

    /** The tag names the SPI, so one store getting slow is attributable rather than aggregate. */
    @Test
    void storeMetersIdentifyWhichStoreWasCalled() {
        chat();

        assertTrue(scrape().contains("store=\"WorkingMemoryStore\""),
                "a turn appends to working memory, so that store must appear by name");
    }

    /**
     * The breaker gauges are registered at startup rather than on first failure, so a healthy agent
     * still publishes a series. A gauge that only appears once things break is useless for spotting
     * the moment they do.
     */
    @Test
    void circuitBreakerStateIsPublishedForBothModelSurfaces() {
        String metrics = scrape();

        assertTrue(metrics.contains("qlawkus_model_circuit_state"),
                "breaker state must be gauged; scrape was:\n" + metrics);
        assertTrue(metrics.contains("surface=\"chat\"") && metrics.contains("surface=\"embedding\""),
                "chat and embeddings fail independently, so each needs its own series");
    }

    private void chat() {
        agentService.chat("markdown-only-cognition-telemetry", "Say exactly: pong")
                .collect().asList().await().atMost(Duration.ofSeconds(30));
    }

    private static String scrape() {
        return given().when().get("/q/metrics").then().statusCode(200).extract().asString();
    }
}
