package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(TelegramToolProgressProfile.class)
class TelegramFileDeliveryTest {

    private static final String SEND_DOCUMENT = "/bottest-token/sendDocument";
    private static final String REPORT = "item,count\nbooks,2\n";

    WireMock wiremock;

    @Inject
    TelegramProviderAdapter adapter;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
        wiremock.resetRequests();
    }

    @Test
    void sendsAMultipartDocumentToTheOriginalChat() {
        wiremock.register(post(urlEqualTo(SEND_DOCUMENT))
                .willReturn(okJson("{\"ok\":true,\"result\":{\"message_id\":7}}")));

        adapter.sendFile("123", "report.csv", "text/csv", REPORT.getBytes(StandardCharsets.UTF_8))
                .await().atMost(Duration.ofSeconds(5));

        wiremock.verifyThat(postRequestedFor(urlEqualTo(SEND_DOCUMENT))
                .withRequestBody(containing("name=\"chat_id\""))
                .withRequestBody(containing("\r\n\r\n123\r\n"))
                .withRequestBody(containing("name=\"document\"; filename=\"report.csv\""))
                .withRequestBody(containing(REPORT)));
    }

    @Test
    void failsIfTelegramDoesNotAcknowledgeTheDocument() {
        wiremock.register(post(urlEqualTo(SEND_DOCUMENT)).willReturn(okJson("{\"ok\":false}")));

        assertThrows(IllegalStateException.class, () -> adapter
                .sendFile("123", "report.csv", "text/csv", REPORT.getBytes(StandardCharsets.UTF_8))
                .await().atMost(Duration.ofSeconds(5)));
        wiremock.verifyThat(1, postRequestedFor(urlEqualTo(SEND_DOCUMENT)));
    }
}
