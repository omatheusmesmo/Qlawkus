package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(TelegramToolProgressDeleteProfile.class)
class TelegramToolProgressDeleteTest {

    private static final String SEND = "/bottest-token/sendMessage";

    WireMock wiremock;

    private String token;

    @Inject
    MessagingOrchestrator orchestrator;

    @BeforeEach
    void stubPlatform() {
        wiremock.resetToDefaultMappings();
        ModelStubs.register(wiremock);
        token = ModelStubs.newToken();
        wiremock.register(post(urlEqualTo(SEND)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":{\"message_id\":777}}")));
        wiremock.register(post(urlEqualTo("/bottest-token/deleteMessage")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":true}")));
        wiremock.register(post(urlEqualTo("/bottest-token/editMessageText")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":true}")));
    }

    @Test
    void onCompleteDeleteRemovesTheStatusMessageAndTheReplyStillArrives() {
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("queryMetrics", "{\"query\":\"up\"}"),
                ModelStubs.text("All good."));

        orchestrator.process(MessagingMessage.text("telegram", "201", "201", "status? " + token)).await().indefinitely();

        List<String> bodies = bodiesFor("201");
        List<String> status = bodies.stream().filter(body -> !body.contains("parse_mode")).toList();
        assertEquals(1, status.size());
        assertTrue(status.get(0).contains("\"text\":\"📊 queryMetrics\""), "status mode shows the name only");
        assertEquals(1, wiremock.findAll(postRequestedFor(urlEqualTo("/bottest-token/deleteMessage"))).stream()
                .map(LoggedRequest::getBodyAsString)
                .filter(body -> body.contains("\"chat_id\":\"201\""))
                .count());
        assertEquals(0, wiremock.findAll(postRequestedFor(urlEqualTo("/bottest-token/editMessageText"))).stream()
                .map(LoggedRequest::getBodyAsString)
                .filter(body -> body.contains("\"chat_id\":\"201\""))
                .count());
        assertEquals(1, bodies.stream().filter(body -> body.contains("parse_mode")).count());
    }

    private List<String> bodiesFor(String chatId) {
        return wiremock.findAll(postRequestedFor(urlEqualTo(SEND))).stream()
                .map(LoggedRequest::getBodyAsString)
                .filter(body -> body.contains("\"chat_id\":\"" + chatId + "\""))
                .toList();
    }
}
