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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class SlackWebhookTest {

    @InjectMock
    MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void webhook_urlVerificationReturnsChallengeAndDoesNotCallOrchestrator() {
        String payload = """
                {"type": "url_verification", "challenge": "abc123"}
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/slack")
                .then()
                .statusCode(200)
                .body("challenge", org.hamcrest.Matchers.equalTo("abc123"));

        verify(orchestrator, never()).process(any());
    }

    @Test
    void webhook_eventCallbackMessageForwardsToOrchestrator() {
        String payload = """
                {
                  "type": "event_callback",
                  "event": {
                    "type": "message",
                    "user": "user-allowed",
                    "channel": "C123",
                    "text": "hello slack"
                  }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/slack")
                .then()
                .statusCode(200);

        ArgumentCaptor<MessagingMessage> captor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator, timeout(2000)).process(captor.capture());

        MessagingMessage captured = captor.getValue();
        assertEquals("slack", captured.providerId());
        assertEquals("C123", captured.chatId());
        assertEquals("user-allowed", captured.userId());
        assertEquals("hello slack", captured.text());
    }
}
