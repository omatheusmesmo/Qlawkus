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
class DiscordWebhookTest {

    @InjectMock
    MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void webhook_pingReturnsType1AndDoesNotCallOrchestrator() {
        String payload = """
                {"id": "int-1", "type": 1}
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/discord")
                .then()
                .statusCode(200)
                .body("type", org.hamcrest.Matchers.equalTo(1));

        verify(orchestrator, never()).process(any());
    }

    @Test
    void webhook_applicationCommandReturnsDeferredAndForwardsToOrchestrator() {
        String payload = """
                {
                  "id": "int-2",
                  "type": 2,
                  "channel_id": "chan-1",
                  "member": {"user": {"id": "user-allowed"}},
                  "data": {"options": [{"value": "hello discord"}]}
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/discord")
                .then()
                .statusCode(200)
                .body("type", org.hamcrest.Matchers.equalTo(5));

        ArgumentCaptor<MessagingMessage> captor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator, timeout(2000)).process(captor.capture());

        MessagingMessage captured = captor.getValue();
        assertEquals("discord", captured.providerId());
        assertEquals("user-allowed", captured.userId());
        assertEquals("hello discord", captured.text());
    }
}
