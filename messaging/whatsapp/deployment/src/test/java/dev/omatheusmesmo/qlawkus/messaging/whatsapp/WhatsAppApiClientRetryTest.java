package dev.omatheusmesmo.qlawkus.messaging.whatsapp;

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
class WhatsAppApiClientRetryTest {

    WireMock wiremock;

    @Inject
    @RestClient
    WhatsAppApiClient client;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetScenarios();
    }

    @Test
    void aTransientFailureIsRetriedUntilSuccess() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v18.0/123/messages"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("second attempt"));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v18.0/123/messages"))
                .inScenario("retry")
                .whenScenarioStateIs("second attempt")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"messaging_product\":\"whatsapp\"}")));

        client.sendMessage("123", "Bearer test-token", textMessage());

        wiremock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v18.0/123/messages")));
    }

    @Test
    void aPermanentFailureIsNotRetried() {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v18.0/123/messages"))
                .willReturn(WireMock.aResponse().withStatus(401)));

        assertThrows(ClientWebApplicationException.class,
                () -> client.sendMessage("123", "Bearer test-token", textMessage()));

        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v18.0/123/messages")));
    }

    /**
     * Built via the public canonical constructor rather than {@code SendMessageRequest.textTo} - that
     * factory is package-private and {@code @QuarkusTest} loads test classes and application classes
     * on separate classloaders, so a package-private cross-class call throws {@link
     * IllegalAccessError} even though both classes report the same package name.
     */
    private static WhatsAppApiClient.SendMessageRequest textMessage() {
        return new WhatsAppApiClient.SendMessageRequest(
                "whatsapp", "individual", "5511999999999", "text",
                new WhatsAppApiClient.TextBody("hello"));
    }
}
