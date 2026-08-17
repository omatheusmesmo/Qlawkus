package dev.omatheusmesmo.qlawkus.tools.google.drive;

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
class GoogleDriveDownloadClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    GoogleDriveDownloadClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/drive/v3/files/file-1?alt=media"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/drive/v3/files/file-1?alt=media"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("file contents")));

        client.downloadFile("file-1", "media");

        wiremock.verifyThat(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/drive/v3/files/file-1?alt=media")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.get(WireMock.urlEqualTo("/drive/v3/files/file-1?alt=media"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class, () -> client.downloadFile("file-1", "media"));

        wiremock.verifyThat(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/drive/v3/files/file-1?alt=media")));
    }
}
