package dev.omatheusmesmo.qlawkus.tools.google.storage;

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
 * Transient failures (429, 5xx, network) are retried with backoff; permanent failures (401, 404, ...)
 * are not, since retrying those cannot succeed and only delays surfacing the real problem.
 */
@QuarkusTest
@ConnectWireMock
class GoogleStorageRestClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    GoogleStorageRestClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/storage/v1/b/my-bucket/o/my-object"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/storage/v1/b/my-bucket/o/my-object"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"my-object\",\"bucket\":\"my-bucket\"}")));

        client.getObjectMetadata("my-bucket", "my-object");

        wiremock.verifyThat(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/storage/v1/b/my-bucket/o/my-object")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/storage/v1/b/my-bucket/o/my-object"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class,
                () -> client.getObjectMetadata("my-bucket", "my-object"));

        wiremock.verifyThat(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/storage/v1/b/my-bucket/o/my-object")));
    }
}
