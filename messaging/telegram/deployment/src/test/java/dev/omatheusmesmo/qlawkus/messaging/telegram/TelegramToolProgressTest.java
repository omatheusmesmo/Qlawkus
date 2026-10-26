package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
@TestProfile(TelegramToolProgressProfile.class)
class TelegramToolProgressTest {

    private static final String SEND = "/bottest-token/sendMessage";
    private static final String EDIT = "/bottest-token/editMessageText";
    private static final String GITHUB_TOKEN = "ghp_AbC123AbC123AbC123AbC123AbC123AbC123";

    WireMock wiremock;

    @Inject
    MessagingOrchestrator orchestrator;

    private String token;

    @BeforeEach
    void stubPlatformAndModel() {
        wiremock.resetToDefaultMappings();
        ModelStubs.register(wiremock);
        token = ModelStubs.newToken();
        wiremock.register(post(urlEqualTo(SEND)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":{\"message_id\":555}}")));
        wiremock.register(post(urlEqualTo(EDIT)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":true}")));
        wiremock.register(post(urlEqualTo("/bottest-token/deleteMessage")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":true,\"result\":true}")));
    }

    @Test
    void twoToolCallsShareOneStatusMessageThatIsEditedToASummary() {
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("queryMetrics", "{\"query\":\"rate(qlawkus_tool_calls_total[1h])\"}"),
                ModelStubs.toolCall("webSearch", "{\"q\":\"weather in lisbon\"}"),
                ModelStubs.text("It is sunny."));

        run("101");

        List<String> status = statusPosts("101");
        assertEquals(1, status.size(), "one status message per turn");
        assertTrue(status.get(0).contains("📊 queryMetrics"));
        List<String> edits = editsFor("101");
        assertEquals(1, edits.size(), "the throttle lets the two quick calls collapse into one edit");
        assertTrue(edits.get(0).matches(".*2 tools, \\d+\\.\\ds.*"), edits.get(0));
        assertEquals(1, replies("101").size());
        assertTrue(replies("101").get(0).contains("sunny"));
        assertFalse(bodiesFor("101").stream().anyMatch(body -> body.contains(ProgressProbeTools.RESULT)),
                "tool results are never posted");
    }

    @Test
    void aTurnWithoutToolsPostsNoStatusMessage() {
        ModelStubs.script(wiremock, token, ModelStubs.text("Hello there."));

        run("102");

        assertEquals(0, statusPosts("102").size());
        assertEquals(0, editsFor("102").size());
        assertEquals(1, replies("102").size());
    }

    @Test
    void verboseCommandTurnsProgressOffForThatChat() {
        run("103", "/verbose");
        run("103", "/verbose");
        assertTrue(replies("103").get(0).contains("verbose"));
        assertTrue(replies("103").get(1).contains("off"));
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("webSearch", "{\"q\":\"x\"}"),
                ModelStubs.text("Done."));

        run("103");

        assertEquals(0, statusPosts("103").size());
        assertEquals(0, editsFor("103").size());
    }

    @Test
    void aSecretInToolArgumentsNeverReachesThePlatform() {
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("shellExec", "{\"token\":\"" + GITHUB_TOKEN + "\"}"),
                ModelStubs.text("Done."));

        run("104");

        assertFalse(bodiesFor("104").stream().anyMatch(body -> body.contains("ghp_AbC123")),
                "the GitHub token must never be sent");
        assertTrue(statusPosts("104").get(0).contains("[redacted]"));
    }

    @Test
    void anEditFailureStopsProgressAndTheReplyStillArrives() {
        wiremock.register(post(urlEqualTo(EDIT)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}")));
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("webSearch", "{\"q\":\"x\"}"),
                ModelStubs.text("Answer."));

        assertDoesNotThrow(() -> run("105"));

        assertEquals(1, statusPosts("105").size());
        assertEquals(1, editsFor("105").size(), "the failed edit is attempted once, then progress stops");
        assertEquals(1, replies("105").size());
    }

    @Test
    void aToolRunsOnTheTurnThreadWhereTheDeliveryContextIsVisible() {
        ProgressProbeTools.DELIVERY_SEEN.clear();
        ModelStubs.script(wiremock, token,
                ModelStubs.toolCall("recordDeliveryContext", "{}"),
                ModelStubs.text("Recorded."));

        run("106");

        assertEquals(List.of("106"), ProgressProbeTools.DELIVERY_SEEN,
                "a tool in a messaging turn must see the chat it answers, as on the caller thread");
        assertEquals(1, replies("106").size());
    }

    private void run(String chatId) {
        run(chatId, "hello " + token);
    }

    private void run(String chatId, String text) {
        orchestrator.process(MessagingMessage.text("telegram", chatId, chatId, text)).await().indefinitely();
    }

    private List<String> statusPosts(String chatId) {
        return bodiesFor(chatId).stream().filter(body -> !body.contains("parse_mode")).toList();
    }

    private List<String> replies(String chatId) {
        return bodiesFor(chatId).stream().filter(body -> body.contains("parse_mode")).toList();
    }

    private List<String> editsFor(String chatId) {
        return wiremock.findAll(postRequestedFor(urlEqualTo(EDIT))).stream()
                .map(LoggedRequest::getBodyAsString)
                .filter(body -> body.contains("\"chat_id\":\"" + chatId + "\""))
                .map(body -> body.replaceAll(".*\"text\":\"", "").replaceAll("\"}$", ""))
                .toList();
    }

    private List<String> bodiesFor(String chatId) {
        return wiremock.findAll(postRequestedFor(urlEqualTo(SEND))).stream()
                .map(LoggedRequest::getBodyAsString)
                .filter(body -> body.contains("\"chat_id\":\"" + chatId + "\""))
                .toList();
    }
}
