package dev.omatheusmesmo.qlawkus.tools.skillhub;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link HttpSkillHub#get} through {@link HttpSkillHub#search}: transient failures (429,
 * 5xx, network) are retried with backoff; permanent failures (404, for instance) are not, since
 * retrying those cannot succeed and only delays surfacing the real problem. Unlike
 * {@link HttpSkillHubTest}, this boots real CDI so the {@code @Retry} interceptor actually applies -
 * a manually constructed {@link HttpSkillHub} bypasses it entirely.
 */
@QuarkusTest
@ConnectWireMock
class HttpSkillHubRetryTest {

    WireMock wiremock;

    @Inject
    HttpSkillHub hub;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.get(WireMock.urlPathEqualTo("/api/search"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.get(WireMock.urlPathEqualTo("/api/search"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"name":"triage-inbox","description":"Triage your inbox","source":"acme/triage-inbox"}]""")));

        var hits = hub.search("triage", 10);

        assertEquals(1, hits.size());
        wiremock.verifyThat(2, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/search")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.get(WireMock.urlPathEqualTo("/api/search"))
                .willReturn(WireMock.aResponse().withStatus(404)));

        assertThrows(IllegalStateException.class, () -> hub.search("triage", 10));

        wiremock.verifyThat(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/search")));
    }
}
