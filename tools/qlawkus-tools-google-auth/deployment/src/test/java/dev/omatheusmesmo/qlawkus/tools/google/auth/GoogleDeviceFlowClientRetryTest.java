package dev.omatheusmesmo.qlawkus.tools.google.auth;

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
 * Transient failures (429, 5xx, network) are retried with backoff; permanent failures (a rejected
 * grant, for instance) are not, since retrying those cannot succeed and only delays surfacing the
 * real problem.
 */
@QuarkusTest
@ConnectWireMock
class GoogleDeviceFlowClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    GoogleDeviceFlowClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/token"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/token"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"tok\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));

        client.refreshAccessToken("client-id", "client-secret", "refresh-token", "refresh_token");

        wiremock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/token")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/token"))
                .willReturn(WireMock.aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));

        assertThrows(ClientWebApplicationException.class,
                () -> client.refreshAccessToken("client-id", "client-secret", "refresh-token", "refresh_token"));

        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/token")));
    }
}
