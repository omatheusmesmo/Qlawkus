package dev.omatheusmesmo.qlawkus.it.google;

import java.time.Duration;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@ConnectWireMock
@Execution(ExecutionMode.SAME_THREAD)
class GoogleToolLlmIT {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @BeforeEach
    void rateLimitPause() {
    }

    @BeforeEach
    void stubGoogleApis() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);

        wiremock.register(WireMock.get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "items": [
                                    {
                                      "summary": "Team Standup",
                                      "start": { "dateTime": "2026-05-10T09:00:00-03:00" },
                                      "end": { "dateTime": "2026-05-10T09:30:00-03:00" },
                                      "location": "Zoom",
                                      "attendees": []
                                    }
                                  ]
                                }
                                """)));

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

        wiremock.register(WireMock.get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [
                                    {
                                      "id": "file-1",
                                      "name": "report.pdf",
                                      "mimeType": "application/pdf",
                                      "modifiedTime": "2026-05-10T12:00:00Z",
                                      "size": "1024",
                                      "webViewLink": "https://drive.google.com/file/d/file-1"
                                    }
                                  ]
                                }
                                """)));
    }

    @Test
    void llm_invokesListEventsTool() {
        String response = agentService.chatSync("it-test",
                "List my calendar events for the next 7 days. Use the listEvents tool.");

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("Team Standup", "event", "calendar"));
    }

    @Test
    void llm_invokesListEmailsTool() {
        String response = agentService.chatSync("it-test",
                "Check my recent emails. Use the listEmails tool.");

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("email", "message", "Weekly Update"));
    }

    @Test
    void llm_invokesListFilesTool() {
        String response = agentService.chatSync("it-test",
                "List my Google Drive files. Use the listDriveFiles tool.");

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("report", "file", "drive"));
    }

    @Test
    void llm_streaming_invokesListEventsTool() {
        String response = agentService.chat("it-test", "List my calendar events. Use the listEvents tool.")
                .collect()
                .asList()
                .await()
                .atMost(Duration.ofSeconds(120))
                .stream()
                .collect(Collectors.joining());

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("Team Standup", "event", "calendar"));
    }
}
