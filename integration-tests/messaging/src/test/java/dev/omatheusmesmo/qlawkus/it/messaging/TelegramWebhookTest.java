package dev.omatheusmesmo.qlawkus.it.messaging;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@QuarkusTest
class TelegramWebhookTest {

    @InjectMock
    MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void webhook_acceptsValidUpdateAndForwardsToOrchestrator() {
        String payload = """
                {
                  "update_id": 1,
                  "message": {
                    "message_id": 10,
                    "from": {"id": 42, "first_name": "Alice", "username": "alice"},
                    "chat": {"id": 42, "type": "private"},
                    "text": "hello bot"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/telegram")
                .then()
                .statusCode(200);

        ArgumentCaptor<MessagingMessage> captor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator, timeout(2000)).process(captor.capture());

        MessagingMessage captured = captor.getValue();
        assertEquals("telegram", captured.providerId());
        assertEquals("42", captured.chatId());
        assertEquals("42", captured.userId());
        assertEquals("hello bot", captured.text());
        assertTrue(captured.audio().isEmpty());
    }

    @Test
    void webhook_emptyBodyReturns200WithoutCallingOrchestrator() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/webhook/telegram")
                .then()
                .statusCode(200);

        await().pollDelay(Duration.ofMillis(300))
                .atMost(500, TimeUnit.MILLISECONDS)
                .until(() -> true);
        verify(orchestrator, org.mockito.Mockito.never()).process(any());
    }
}
