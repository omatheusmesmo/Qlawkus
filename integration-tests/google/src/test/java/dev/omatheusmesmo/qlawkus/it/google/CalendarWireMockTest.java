package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@ConnectWireMock
class CalendarWireMockTest {

    WireMock wiremock;

    @Inject
    @QlawTool
    CalendarTool calendarTool;

    @Test
    void listEvents_returnsEventsFromStub() {
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

        String result = calendarTool.listEvents(7);

        assertNotNull(result);
        assertTrue(result.contains("Team Standup"), "Should contain event summary. Got: " + result);
    }

    @Test
    void createEvent_returnsCreatedEvent() {
        wiremock.register(WireMock.post(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "summary": "Sprint Review",
                                  "start": { "dateTime": "2026-05-12T14:00:00-03:00" },
                                  "end": { "dateTime": "2026-05-12T15:00:00-03:00" },
                                  "location": "Room 3",
                                  "attendees": []
                                }
                                """)));

        String result = calendarTool.createEvent(
                "Sprint Review",
                "2026-05-12T14:00:00-03:00",
                "2026-05-12T15:00:00-03:00",
                "Room 3",
                null);

        assertTrue(result.contains("Sprint Review"), "Should contain created event. Got: " + result);
    }

    @Test
    void checkAvailability_returnsBusySlots() {
        wiremock.register(WireMock.post(urlPathEqualTo("/calendar/v3/freeBusy"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "calendars": {
                                    "primary": {
                                      "busy": [
                                        { "start": "2026-05-10T09:00:00Z", "end": "2026-05-10T10:00:00Z" }
                                      ]
                                    }
                                  }
                                }
                                """)));

        String result = calendarTool.checkAvailability("2026-05-10");

        assertTrue(result.contains("Busy slots"), "Should report busy slots. Got: " + result);
    }

    @Test
    void suggestFocusTime_returnsFreeSlots() {
        wiremock.register(WireMock.post(urlPathEqualTo("/calendar/v3/freeBusy"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "calendars": {
                                    "primary": {
                                      "busy": [
                                        { "start": "2026-05-10T09:00:00Z", "end": "2026-05-10T10:00:00Z" }
                                      ]
                                    }
                                  }
                                }
                                """)));

        String result = calendarTool.suggestFocusTime("2026-05-10");

        assertTrue(result.contains("Focus time"), "Should suggest focus time slots. Got: " + result);
    }
}
