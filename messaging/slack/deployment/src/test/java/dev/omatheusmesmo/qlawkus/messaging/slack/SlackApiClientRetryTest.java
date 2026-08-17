package dev.omatheusmesmo.qlawkus.messaging.slack;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Transient failures (429, 5xx, network) are retried with backoff; permanent failures (a bad token,
 * for instance) are not, since retrying those cannot succeed and only delays surfacing the real
 * problem.
 */
@QuarkusTest
@ConnectWireMock
class SlackApiClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    SlackApiClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat.postMessage"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat.postMessage"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        client.postMessage("Bearer xoxb-test", new SlackApiClient.PostMessageRequest("C123", "hello", true));

        wiremock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/api/chat.postMessage")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat.postMessage"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class,
                () -> client.postMessage("Bearer xoxb-test",
                        new SlackApiClient.PostMessageRequest("C123", "hello", true)));

        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/api/chat.postMessage")));
    }
}
