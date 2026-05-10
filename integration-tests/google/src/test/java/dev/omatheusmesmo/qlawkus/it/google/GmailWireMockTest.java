package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.GmailTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@ConnectWireMock
class GmailWireMockTest {

    WireMock wiremock;

    @Inject
    @ClawTool
    GmailTool gmailTool;

    @BeforeEach
    void stubListAndMessage() {
        wiremock.register(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "messages": [{ "id": "msg-1" }]
                                }
                                """)));

        wiremock.register(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .withQueryParam("format", equalTo("metadata"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-1",
                                  "snippet": "Please review the Q3 report",
                                  "headers": [
                                    { "name": "Subject", "value": "Weekly Update" },
                                    { "name": "From", "value": "boss@example.com" },
                                    { "name": "Date", "value": "Mon, 10 May 2026 09:00:00 -0300" }
                                  ]
                                }
                                """)));
    }

    @Test
    void listEmails_returnsMessageSummaries() {
        String result = gmailTool.listEmails(null, 5);

        assertTrue(result.contains("Weekly Update"), "Should contain email subject. Got: " + result);
        assertTrue(result.contains("boss@example.com"), "Should contain sender. Got: " + result);
    }

    @Test
    void searchEmails_returnsFilteredMessages() {
        String result = gmailTool.searchEmails("subject:update", 5);

        assertTrue(result.contains("Weekly Update"), "Should contain search result. Got: " + result);
    }

    @Test
    void sendEmail_returnsSuccess() {
        wiremock.register(WireMock.post(urlPathEqualTo("/gmail/v1/users/me/messages/send"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ \"id\": \"sent-1\", \"labelIds\": [\"SENT\"] }")));

        String result = gmailTool.sendEmail("team@example.com", "Test Subject", "Hello team");

        assertTrue(result.contains("Email sent"), "Should confirm email sent. Got: " + result);
    }
}
