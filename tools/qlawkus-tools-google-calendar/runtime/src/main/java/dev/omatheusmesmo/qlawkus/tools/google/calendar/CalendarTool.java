package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEvent;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventAttendee;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.EventDateTime;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ClawTool
@ApplicationScoped
public class CalendarTool {

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Inject
    GoogleCalendarConfig config;

    @Inject
    @RestClient
    GoogleCalendarRestClient calendarClient;

    @Inject
    TimezoneNormalizer timezoneNormalizer;

    @Tool("List upcoming Google Calendar events for the next N days. Returns event summary, time, location and attendees.")
    public String listEvents(int days) {
        if (days <= 0) {
            days = config.defaultDaysRange();
        }

        String timeMin = OffsetDateTime.now(ZoneOffset.UTC).format(RFC3339);
        String timeMax = LocalDate.now(ZoneOffset.UTC)
                .plusDays(days)
                .atTime(23, 59, 59)
                .atOffset(ZoneOffset.UTC)
                .format(RFC3339);

        try {
            CalendarEventList result = calendarClient.listEvents(
                    config.calendarId(),
                    timeMin,
                    timeMax,
                    config.maxResults(),
                    "startTime",
                    true);

            if (result.items().isEmpty()) {
                return "No upcoming events found in the next " + days + " days.";
            }

            return result.items().stream()
                    .map(this::formatEvent)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            Log.errorf(e, "Failed to list Google Calendar events");
            return "Error listing calendar events: " + e.getMessage();
        }
    }

    @Tool("Create a Google Calendar event. Provide summary, start time and end time. Optionally provide location and attendee email addresses.")
    public String createEvent(
            @P("Event title or summary") String summary,
            @P("Start time, e.g. 2026-05-10T14:00:00-03:00") OffsetDateTime startTime,
            @P("End time, e.g. 2026-05-10T15:00:00-03:00") OffsetDateTime endTime,
            @P(value = "Location of the event", required = false) String location,
            @P(value = "List of attendee email addresses", required = false) List<String> attendeeEmails) {

        EventDateTime start = new EventDateTime(startTime.format(RFC3339), null, null);
        EventDateTime end = new EventDateTime(endTime.format(RFC3339), null, null);

        List<CalendarEventAttendee> attendees = List.of();
        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            attendees = attendeeEmails.stream()
                    .map(email -> new CalendarEventAttendee(email, null, null))
                    .toList();
        }

        CalendarEvent event = new CalendarEvent(
                null, summary, null, location, start, end, null, attendees);

        try {
            CalendarEvent created = calendarClient.createEvent(config.calendarId(), event);
            return "Event created: " + formatEvent(created);
        } catch (Exception e) {
            Log.errorf(e, "Failed to create Google Calendar event");
            return "Error creating calendar event: " + e.getMessage();
        }
    }

    @Tool("Check calendar availability for a given date. Returns busy time ranges so you can find free slots.")
    public String checkAvailability(
            @P("Date to check, e.g. 2026-05-10") LocalDate date) {

        String timeMin = date.atTime(OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC)).format(RFC3339);
        String timeMax = date.atTime(OffsetTime.of(23, 59, 59, 0, ZoneOffset.UTC)).format(RFC3339);

        FreeBusyRequest request = new FreeBusyRequest(
                timeMin, timeMax,
                List.of(new FreeBusyRequest.FreeBusyItem(config.calendarId())));

        try {
            FreeBusyResponse response = calendarClient.queryFreeBusy(request);
            FreeBusyResponse.FreeBusyCalendar calendar = response.calendars().get(config.calendarId());

            if (calendar == null || calendar.busy().isEmpty()) {
                return "No busy slots on " + date + ". Full day available.";
            }

            StringBuilder sb = new StringBuilder("Busy slots on " + date + ":\n");
            for (FreeBusyResponse.TimeRange range : calendar.busy()) {
                sb.append("- ").append(timezoneNormalizer.displayLocal(range.start()))
                        .append(" to ").append(timezoneNormalizer.displayLocal(range.end())).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            Log.errorf(e, "Failed to check calendar availability");
            return "Error checking availability: " + e.getMessage();
        }
    }

    @Tool("Find available focus time slots of at least 2 hours on a given date. Uses freeBusy data and working hours to suggest gaps.")
    public String suggestFocusTime(
            @P("Date to check, e.g. 2026-05-10") LocalDate date) {

        OffsetDateTime dayStart = date.atTime(config.workDayStart(), 0, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime dayEnd = date.atTime(config.workDayEnd(), 0, 0).atOffset(ZoneOffset.UTC);
        Duration minSlot = Duration.ofHours(config.focusTimeHours());

        FreeBusyRequest request = new FreeBusyRequest(
                dayStart.format(RFC3339),
                dayEnd.format(RFC3339),
                List.of(new FreeBusyRequest.FreeBusyItem(config.calendarId())));

        try {
            FreeBusyResponse response = calendarClient.queryFreeBusy(request);
            FreeBusyResponse.FreeBusyCalendar calendar = response.calendars().get(config.calendarId());

            List<OffsetDateTime> boundaries = new ArrayList<>();
            boundaries.add(dayStart);

            if (calendar != null) {
                for (FreeBusyResponse.TimeRange range : calendar.busy()) {
                    boundaries.add(OffsetDateTime.parse(range.start()));
                    boundaries.add(OffsetDateTime.parse(range.end()));
                }
            }

            boundaries.add(dayEnd);
            boundaries.sort(OffsetDateTime::compareTo);

            List<String> focusSlots = new ArrayList<>();
            for (int i = 1; i < boundaries.size(); i += 2) {
                OffsetDateTime slotStart = boundaries.get(i);
                OffsetDateTime slotEnd = boundaries.get(i + 1);
                Duration gap = Duration.between(slotStart, slotEnd);
                if (!gap.minus(minSlot).isNegative()) {
                    focusSlots.add(String.format("- %s to %s (%dh%dm free)",
                            timezoneNormalizer.displayLocal(slotStart),
                            timezoneNormalizer.displayLocal(slotEnd),
                            gap.toHours(),
                            gap.toMinutesPart()));
                }
            }

            if (focusSlots.isEmpty()) {
                return "No focus time slots of " + config.focusTimeHours() + "h+ available on " + date
                        + " within working hours (" + config.workDayStart() + ":00-" + config.workDayEnd() + ":00).";
            }

            return "Focus time slots on " + date + ":\n" + String.join("\n", focusSlots);
        } catch (Exception e) {
            Log.errorf(e, "Failed to suggest focus time");
            return "Error suggesting focus time: " + e.getMessage();
        }
    }

    private String formatEvent(CalendarEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.summary() != null ? event.summary() : "(no title)");

        if (event.start() != null) {
            String start = event.start().dateTime() != null
                    ? timezoneNormalizer.displayLocal(event.start().dateTime())
                    : event.start().date();
            sb.append(" | Start: ").append(start);
        }
        if (event.end() != null) {
            String end = event.end().dateTime() != null
                    ? timezoneNormalizer.displayLocal(event.end().dateTime())
                    : event.end().date();
            sb.append(" | End: ").append(end);
        }
        if (event.location() != null) {
            sb.append(" | Location: ").append(event.location());
        }
        if (!event.attendees().isEmpty()) {
            sb.append(" | Attendees: ");
            sb.append(event.attendees().stream()
                    .map(a -> a.displayName() != null ? a.displayName() : a.email())
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}
