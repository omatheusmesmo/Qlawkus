package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyRequest;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Transient failures (429, 5xx, network) are retried with backoff; permanent failures (401, 404, ...)
 * are not, since retrying those cannot succeed and only delays surfacing the real problem.
 */
@QuarkusTest
@ConnectWireMock
class GoogleCalendarRestClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    GoogleCalendarRestClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/calendar/v3/freeBusy"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/calendar/v3/freeBusy"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"calendars\":{}}")));

        client.queryFreeBusy(new FreeBusyRequest(null, null, null));

        wiremock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/calendar/v3/freeBusy")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/calendar/v3/freeBusy"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class,
                () -> client.queryFreeBusy(new FreeBusyRequest(null, null, null)));

        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/calendar/v3/freeBusy")));
    }
}
