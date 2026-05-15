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
class WhatsAppWebhookTest {

    @InjectMock
    MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void verify_returnsChallengeWhenTokenMatches() {
        given()
                .queryParam("hub.mode", "subscribe")
                .queryParam("hub.verify_token", "test-verify-token")
                .queryParam("hub.challenge", "challenge-value")
                .when()
                .get("/api/webhook/whatsapp")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.equalTo("challenge-value"));
    }

    @Test
    void verify_returns403WhenTokenMismatches() {
        given()
                .queryParam("hub.mode", "subscribe")
                .queryParam("hub.verify_token", "wrong-token")
                .queryParam("hub.challenge", "challenge-value")
                .when()
                .get("/api/webhook/whatsapp")
                .then()
                .statusCode(403);
    }

    @Test
    void webhook_textMessageForwardsToOrchestrator() {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "entry-1",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "messages": [{
                          "id": "msg-1",
                          "from": "user-allowed",
                          "type": "text",
                          "text": {"body": "hello whatsapp"}
                        }]
                      }
                    }]
                  }]
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/webhook/whatsapp")
                .then()
                .statusCode(200);

        ArgumentCaptor<MessagingMessage> captor = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator, timeout(2000)).process(captor.capture());

        MessagingMessage captured = captor.getValue();
        assertEquals("whatsapp", captured.providerId());
        assertEquals("user-allowed", captured.userId());
        assertEquals("hello whatsapp", captured.text());
    }

    @Test
    void webhook_emptyBodyReturns200WithoutCallingOrchestrator() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/webhook/whatsapp")
                .then()
                .statusCode(200);

        verify(orchestrator, never()).process(any());
    }
}
