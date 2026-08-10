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
 * Pins what quarkus-langchain4j contributes from the presence of Micrometer alone, so the claim that
 * the agent's model telemetry needs no instrumentation is checked rather than trusted. If an upgrade
 * renames or drops one of these meters, this fails instead of a dashboard quietly going blank.
 *
 * <p>The turn runs against the WireMock stubs this module already uses, so the assertions describe
 * the metric surface without depending on a real provider.
 */
@QuarkusTest
@ConnectWireMock
class TelemetryTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @BeforeEach
    void stubTheModel() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @Test
    void aiServiceCallsAreTimedAndCounted() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("langchain4j_aiservices_timed_seconds"),
                "AI service timings must be reported; scrape was:\n" + metrics);
        assertTrue(metrics.contains("langchain4j_aiservices_counted_total"),
                "AI service invocations must be counted; scrape was:\n" + metrics);
    }

    @Test
    void aiServiceMetersCarryTheServiceAndMethod() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("aiservice=\"AgentService\""),
                "without the aiservice tag the meters cannot be split per agent");
        assertTrue(metrics.contains("method=\"chat\""),
                "without the method tag a slow method cannot be told from a slow agent");
    }

    /**
     * Token usage is the meter that makes the cost of a turn visible, and it is the one every
     * decision about prompt size ends up leaning on.
     */
    @Test
    void tokenUsageIsReportedPerType() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("gen_ai_client_token_usage"),
                "token usage must be reported; scrape was:\n" + metrics);
        assertTrue(metrics.contains("gen_ai_token_type=\"input\"")
                        && metrics.contains("gen_ai_token_type=\"output\""),
                "input and output must be separable, since only one of them grows with the prompt");
    }

    /**
     * The distribution is what a counter cannot give: per-request granularity. A counter sums at
     * record time, so a hundred ordinary turns and one enormous one are indistinguishable in it -
     * and "is my p95 prompt approaching the context limit" is the question that actually drives
     * decisions about what gets injected each turn.
     */
    @Test
    void tokenUsageIsAlsoReportedAsADistribution() {
        chat();
        String metrics = scrape();

        assertTrue(metrics.contains("gen_ai_client_token_usage_distribution"),
                "per-request token distribution must be reported; scrape was:\n" + metrics);
    }

    /**
     * The provider tag is what makes the fallback legible. This agent switches from the primary to
     * Ollama when the circuit opens, and without knowing which one served a turn, every other meter
     * silently mixes two different models.
     */
    @Test
    void metersIdentifyTheProviderThatServedTheTurn() {
        chat();

        assertTrue(scrape().contains("gen_ai_provider_name="),
                "a turn served by the fallback must be distinguishable from one served by the primary");
    }

    /**
     * Cost stays absent until someone enters real prices. An estimate reads as a measurement once it
     * reaches a dashboard, so a default-priced zero would be worse than no series at all.
     */
    @Test
    void costIsAbsentUntilPricesAreConfigured() {
        chat();

        assertTrue(!scrape().contains("gen_ai_client_estimated_cost"),
                "qlawkus.cost.enabled is unset here, so no cost series may be published");
    }

    private void chat() {
        agentService.chat("markdown-only-telemetry", "Say exactly: pong")
                .collect().asList().await().atMost(Duration.ofSeconds(30));
    }

    private static String scrape() {
        return given().when().get("/q/metrics").then().statusCode(200).extract().asString();
    }
}
