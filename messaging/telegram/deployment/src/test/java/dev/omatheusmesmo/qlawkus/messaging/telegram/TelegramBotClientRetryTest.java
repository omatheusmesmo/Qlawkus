package dev.omatheusmesmo.qlawkus.messaging.telegram;

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
 * problem. Does not touch {@link TelegramBotClient#getUpdates}'s long-polling semantics - see the
 * class javadoc there.
 */
@QuarkusTest
@ConnectWireMock
class TelegramBotClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    TelegramBotClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/bottest-token/sendMessage"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/bottest-token/sendMessage"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        client.sendMessage("test-token",
                new TelegramBotClient.SendMessageRequest("123", "hello", null));

        wiremock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/bottest-token/sendMessage")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/bottest-token/sendMessage"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class,
                () -> client.sendMessage("test-token",
                        new TelegramBotClient.SendMessageRequest("123", "hello", null)));

        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/bottest-token/sendMessage")));
    }
}
