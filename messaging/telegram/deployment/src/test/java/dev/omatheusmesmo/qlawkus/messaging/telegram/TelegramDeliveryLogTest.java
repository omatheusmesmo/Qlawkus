package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.messaging.NotificationService;
import dev.omatheusmesmo.qlawkus.messaging.logging.MessagingLogCapture;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(TelegramToolProgressProfile.class)
class TelegramDeliveryLogTest {

    private static final String SEND = "/bottest-token/sendMessage";

    WireMock wiremock;

    @Inject
    NotificationService notificationService;

    @BeforeEach
    void resetWiremock() {
        wiremock.resetToDefaultMappings();
    }

    @Test
    void acceptedReplyLogsPartsAndChannelInMdcAfterTelegramSendsEveryPart() {
        wiremock.register(post(urlEqualTo(SEND)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":{\"message_id\":1}}")));
        String longReply = "word ".repeat(1000);

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            notificationService.send("telegram", "chat-tg-1", longReply, "conv-tg-1").await().indefinitely();

            var delivered = logs.containing("Reply delivered");
            assertEquals(1, delivered.size(), () -> "expected one delivered line, got " + logs.lines());
            String line = delivered.get(0).message();
            assertTrue(line.contains("provider=telegram chatId=chat-tg-1 parts=2 kind=text"), line);
            assertEquals("telegram", delivered.get(0).mdc().get("channel"));
            assertEquals("conv-tg-1", delivered.get(0).mdc().get("conversation"));
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("word word")),
                    () -> "reply text reached a log line: " + logs.lines());
        }
    }

    @Test
    void rejectedReplyWarnsWithExceptionClassAndNoText() {
        wiremock.register(post(urlEqualTo(SEND)).willReturn(aResponse().withStatus(401)));

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            notificationService.send("telegram", "chat-tg-2", "private reply text", "conv-tg-2")
                    .await().indefinitely();

            var failed = logs.containing("Reply delivery failed");
            assertEquals(1, failed.size(), () -> "expected one failure warning, got " + logs.lines());
            assertEquals(Level.WARNING, failed.get(0).level());
            String line = failed.get(0).message();
            assertTrue(line.contains("provider=telegram chatId=chat-tg-2 kind=text sentParts=0 of 1"), line);
            assertTrue(line.contains("exception=ClientWebApplicationException"), line);
            assertEquals("telegram", failed.get(0).mdc().get("channel"));
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("private reply text")),
                    () -> "reply text reached a log line: " + logs.lines());
        }
    }
}
