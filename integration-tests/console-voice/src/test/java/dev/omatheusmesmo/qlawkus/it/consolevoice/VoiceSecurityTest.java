package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The gate in front of the voice socket: the console's credentials are required, and the upgrade's Origin
 * must be the console's own origin. A session that opens with the owner's cached credentials from another
 * site is refused before any microphone event can reach it.
 */
@QuarkusTest
@ConnectWireMock
class VoiceSecurityTest {

    @TestHTTPResource("/")
    URI base;

    @Inject
    BasicWebSocketConnector connector;

    WireMock wiremock;

    @BeforeEach
    void reset() {
        wiremock.resetMappings();
        VoiceTestSupport.stubEmbeddings(wiremock);
    }

    @Test
    void theUpgradeIsRefusedWithoutCredentials() {
        assertThrows(Exception.class, () -> VoiceClient.open(connector, base, VoiceTestSupport.origin(base), null));
    }

    @Test
    void theUpgradeIsRefusedWithWrongCredentials() {
        assertThrows(Exception.class, () -> VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, "wrong-password")));
    }

    @Test
    void theUpgradeIsRefusedForAForeignOrigin() {
        assertThrows(Exception.class, () -> VoiceClient.open(connector, base, "https://evil.example",
                VoiceClient.basic(USER, PASSWORD)));
    }

    @Test
    void theConsoleOwnOriginIsAccepted() {
        try (VoiceClient client = VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD))) {
            assertTrue(client.isOpen());
            client.awaitType("ready");
        }
    }
}
