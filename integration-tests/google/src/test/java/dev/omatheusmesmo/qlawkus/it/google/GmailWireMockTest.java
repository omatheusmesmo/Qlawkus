package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.gmail.GmailTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                          "payload": {
                            "headers": [
                              { "name": "Subject", "value": "Weekly Update" },
                              { "name": "From", "value": "boss@example.com" },
                              { "name": "Date", "value": "Mon, 10 May 2026 09:00:00 -0300" }
                            ]
                          }
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
    void getEmail_returnsFullContent() {
        wiremock.register(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-2"))
                .withQueryParam("format", equalTo("full"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                          "id": "msg-2",
                          "snippet": "This is the full email",
                          "payload": {
                            "headers": [
                              { "name": "Subject", "value": "Full Email Test" },
                              { "name": "From", "value": "alice@example.com" },
                              { "name": "Date", "value": "Tue, 11 May 2026 14:00:00 -0300" }
                            ],
                            "body": {
                              "data": "VGhpcyBpcyB0aGUgZnVsbCBlbWFpbCBib2R5"
                            }
                          }
                        }
                        """)));

        String result = gmailTool.getEmail("msg-2");

        assertTrue(result.contains("Full Email Test"), "Should contain subject. Got: " + result);
        assertTrue(result.contains("alice@example.com"), "Should contain sender. Got: " + result);
        assertTrue(result.contains("This is the full email body"), "Should contain decoded body. Got: " + result);
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

    @Test
    void replyToEmail_returnsSuccess() {
        wiremock.register(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-3"))
            .withQueryParam("format", equalTo("metadata"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                {
                    "id": "msg-3",
                    "threadId": "thread-abc",
                    "snippet": "Original message",
                    "payload": {
                        "headers": [
                            { "name": "Subject", "value": "Hello" },
                            { "name": "From", "value": "bob@example.com" },
                            { "name": "Date", "value": "Wed, 12 May 2026 10:00:00 -0300" }
                        ]
                    }
                }
                """)));

        wiremock.register(WireMock.post(urlPathEqualTo("/gmail/v1/users/me/messages/send"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"id\": \"reply-1\" }")));

        String result = gmailTool.replyToEmail("msg-3", "Thanks!", false);

        assertTrue(result.contains("Reply sent"), "Should confirm reply sent. Got: " + result);
        assertTrue(result.contains("bob@example.com"), "Should address original sender. Got: " + result);
    }

    @Test
    void replyToEmail_replyAll_includesAllRecipients() {
        wiremock.register(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-3b"))
            .withQueryParam("format", equalTo("metadata"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "id": "msg-3b",
                      "threadId": "thread-def",
                      "snippet": "Team update",
                      "payload": {
                        "headers": [
                          { "name": "Subject", "value": "Team update" },
                          { "name": "From", "value": "alice@example.com" },
                          { "name": "To", "value": "bob@example.com, carol@example.com" },
                          { "name": "Cc", "value": "dan@example.com" },
                          { "name": "Date", "value": "Wed, 12 May 2026 10:00:00 -0300" }
                        ]
                      }
                    }
                    """)));

        wiremock.register(WireMock.post(urlPathEqualTo("/gmail/v1/users/me/messages/send"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"id\": \"reply-2\" }")));

        String result = gmailTool.replyToEmail("msg-3b", "Noted!", true);

        assertTrue(result.contains("Reply sent"), "Should confirm reply sent. Got: " + result);
        assertTrue(result.contains("alice@example.com"), "Should include sender. Got: " + result);
        assertTrue(result.contains("bob@example.com"), "Should include To recipients. Got: " + result);
        assertTrue(result.contains("dan@example.com"), "Should include Cc recipients. Got: " + result);
    }

    @Test
    void trashEmail_returnsSuccess() {
        wiremock.register(WireMock.post(urlPathEqualTo("/gmail/v1/users/me/messages/msg-4/trash"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"id\": \"msg-4\", \"labelIds\": [\"TRASH\"] }")));

        String result = gmailTool.trashEmail("msg-4");

        assertTrue(result.contains("moved to trash"), "Should confirm trashed. Got: " + result);
    }

    @Test
    void modifyEmailLabels_returnsSuccess() {
        wiremock.register(WireMock.post(urlPathEqualTo("/gmail/v1/users/me/messages/msg-5/modify"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"id\": \"msg-5\" }")));

        String result = gmailTool.modifyEmailLabels("msg-5", null, List.of("UNREAD"));

        assertTrue(result.contains("Labels updated"), "Should confirm label update. Got: " + result);
        assertTrue(result.contains("UNREAD"), "Should mention removed label. Got: " + result);
    }
}
