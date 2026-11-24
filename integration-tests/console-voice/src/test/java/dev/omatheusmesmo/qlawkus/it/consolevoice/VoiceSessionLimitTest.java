package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.PASSWORD;
import static dev.omatheusmesmo.qlawkus.it.consolevoice.VoiceTestSupport.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.JsonNode;
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
 * One owner holds one live session by default. A second is refused, and closing the first releases its slot,
 * so a dropped connection never leaves the owner locked out.
 */
@QuarkusTest
@ConnectWireMock
class VoiceSessionLimitTest {

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
    void aSecondSessionIsRefusedUntilTheFirstCloses() throws InterruptedException {
        try (VoiceClient first = open()) {
            first.awaitType("ready");
            try (VoiceClient second = open()) {
                VoiceClient.assertCode(second.awaitType("error"), "too-many-sessions");
            }
            first.close();
        }

        assertEquals("ready", openUntilAccepted().path("type").asString());
    }

    private JsonNode openUntilAccepted() throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            VoiceClient client = open();
            JsonNode first = client.nextEvent();
            if ("ready".equals(first.path("type").asString())) {
                client.close();
                return first;
            }
            client.close();
            Thread.sleep(200);
        }
        throw new AssertionError("the owner's slot was never released");
    }

    private VoiceClient open() {
        return VoiceClient.open(connector, base, VoiceTestSupport.origin(base),
                VoiceClient.basic(USER, PASSWORD));
    }
}
