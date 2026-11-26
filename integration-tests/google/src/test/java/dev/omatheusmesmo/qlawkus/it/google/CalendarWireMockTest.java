package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String STANDUP = """
            {
              "id": "%s",
              "summary": "%s",
              "start": { "dateTime": "2026-05-10T12:00:00Z" },
              "end": { "dateTime": "2026-05-10T12:30:00Z" },
              "attendees": [ { "email": "ana@example.com" } ],
              "htmlLink": "https://calendar.google.com/event?eid=%s"
            }
            """;

    private void stubEvent(String id, String title) {
        wiremock.register(WireMock.get(urlPathEqualTo("/calendar/v3/calendars/primary/events/" + id))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(STANDUP.formatted(id, title, id))));
    }

    @Test
    void listEvents_showsTheIdSoTheAgentCanChangeTheEvent() {
        wiremock.register(WireMock.get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[" + STANDUP.formatted("evt-listed", "Planning", "evt-listed") + "]}")));

        String result = calendarTool.listEvents(7);

        assertTrue(result.contains("ID: evt-listed"), "The id must be visible. Got: " + result);
    }

    @Test
    void updateEvent_patchesOnlyTheGivenFieldsAndNotifiesGuests() {
        stubEvent("evt-update", "Standup");
        wiremock.register(WireMock.patch(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-update"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(STANDUP.formatted("evt-update", "Daily sync", "evt-update"))));

        String result = calendarTool.updateEvent("evt-update", "Standup", "Daily sync", null, null, null, null, null);

        assertTrue(result.contains("Daily sync") && result.contains("title"), "Got: " + result);
        wiremock.verifyThat(1, patchRequestedFor(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-update"))
                .withQueryParam("sendUpdates", equalTo("all"))
                .withRequestBody(equalToJson("{\"summary\":\"Daily sync\"}")));
    }

    @Test
    void updateEvent_movesTheEventInUtc() {
        stubEvent("evt-move", "Standup");
        wiremock.register(WireMock.patch(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-move"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(STANDUP.formatted("evt-move", "Standup", "evt-move"))));

        String result = calendarTool.updateEvent("evt-move", "Standup", null,
                "2026-05-11T10:00:00-03:00", "2026-05-11T10:30:00-03:00", null, null, null);

        assertTrue(result.contains("time"), "Got: " + result);
        wiremock.verifyThat(1, patchRequestedFor(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-move"))
                .withRequestBody(equalToJson(
                        "{\"start\":{\"dateTime\":\"2026-05-11T13:00:00Z\"},\"end\":{\"dateTime\":\"2026-05-11T13:30:00Z\"}}")));
    }

    @Test
    void updateEvent_refusesAnEventWhoseTitleIsNotTheOneTheOwnerApproved() {
        stubEvent("evt-wrong", "Board meeting");

        String result = calendarTool.updateEvent("evt-wrong", "Standup", "Renamed", null, null, null, null, null);

        assertTrue(result.contains("Board meeting") && result.contains("Refused"), "Got: " + result);
        wiremock.verifyThat(0, patchRequestedFor(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-wrong")));
    }

    @Test
    void updateEvent_saysWhenThereIsNothingToChange() {
        String result = calendarTool.updateEvent("evt-none", "Standup", null, null, null, null, null, List.of());

        assertTrue(result.contains("Nothing to change"), "Got: " + result);
    }

    @Test
    void updateEvent_asksForBothEndsOfTheTime() {
        String result = calendarTool.updateEvent("evt-half", "Standup", null,
                "2026-05-11T10:00:00-03:00", null, null, null, null);

        assertTrue(result.contains("both startTime and endTime"), "Got: " + result);
    }

    @Test
    void updateEvent_answersAClearMessageForAnUnknownId() {
        wiremock.register(WireMock.get(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-ghost"))
                .willReturn(aResponse().withStatus(404).withBody("{}")));

        String result = calendarTool.updateEvent("evt-ghost", "Standup", "x", null, null, null, null, null);

        assertTrue(result.contains("No event with id evt-ghost"), "Got: " + result);
        assertFalse(result.contains("Exception"), "No stack trace in a tool result. Got: " + result);
    }

    @Test
    void deleteEvent_cancelsTheEventAndNotifiesGuests() {
        stubEvent("evt-delete", "Standup");
        wiremock.register(WireMock.delete(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-delete"))
                .willReturn(aResponse().withStatus(204)));

        String result = calendarTool.deleteEvent("evt-delete", "standup");

        assertTrue(result.contains("deleted") && result.contains("Standup"), "Got: " + result);
        wiremock.verifyThat(1, deleteRequestedFor(urlEqualTo("/calendar/v3/calendars/primary/events/evt-delete?sendUpdates=all")));
    }

    @Test
    void deleteEvent_refusesAnEventWhoseTitleIsNotTheOneTheOwnerApproved() {
        stubEvent("evt-keep", "Board meeting");

        String result = calendarTool.deleteEvent("evt-keep", "Standup");

        assertTrue(result.contains("Refused") && result.contains("Board meeting"), "Got: " + result);
        wiremock.verifyThat(0, deleteRequestedFor(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-keep")));
    }

    @Test
    void deleteEvent_treatsAnAlreadyGoneEventAsSuch() {
        wiremock.register(WireMock.get(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-gone"))
                .willReturn(aResponse().withStatus(410).withBody("{}")));

        String result = calendarTool.deleteEvent("evt-gone", "Standup");

        assertTrue(result.contains("No event with id evt-gone"), "Got: " + result);
        wiremock.verifyThat(0, deleteRequestedFor(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-gone")));
    }
}
