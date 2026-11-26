package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.Logged;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiDiagnostics;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleApiExecutor;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEvent;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventAttendee;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventPatch;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.EventDateTime;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyRequest;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.FreeBusyResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@QlawTool
@ApplicationScoped
@Logged
public class CalendarTool {

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final String NOTIFY_GUESTS = "all";

    @Inject
    GoogleCalendarConfig config;

    @Inject
    @RestClient
    GoogleCalendarRestClient calendarClient;

    @Inject
    TimezoneNormalizer timezoneNormalizer;

    @Inject
    GoogleApiExecutor apiExecutor;

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
            CalendarEventList result = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.listEvents(
                            config.calendarId(),
                            timeMin,
                            timeMax,
                            config.maxResults(),
                            "startTime",
                            true));

            if (result.items().isEmpty()) {
                return "No upcoming events found in the next " + days + " days.";
            }

            return result.items().stream()
                    .map(this::formatEvent)
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            Log.errorf(e, "Failed to list Google Calendar events");
            return diagnoseFailure("list calendar events", e);
        }
    }

    @Tool("Create a Google Calendar event. Provide summary, start time and end time. Optionally provide location and attendee email addresses.")
    public String createEvent(
            @P("Event title or summary") String summary,
            @P("Start time in ISO-8601 format, e.g. 2026-05-10T14:00:00-03:00") String startTime,
            @P("End time in ISO-8601 format, e.g. 2026-05-10T15:00:00-03:00") String endTime,
            @P(value = "Location of the event", required = false) String location,
            @P(value = "List of attendee email addresses", required = false) List<String> attendeeEmails) {

        OffsetDateTime start = OffsetDateTime.parse(startTime).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.parse(endTime).withOffsetSameInstant(ZoneOffset.UTC);

        EventDateTime startDto = new EventDateTime(start.format(RFC3339), null, null);
        EventDateTime endDto = new EventDateTime(end.format(RFC3339), null, null);

        List<CalendarEventAttendee> attendees = List.of();
        if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
            attendees = attendeeEmails.stream()
                    .map(email -> new CalendarEventAttendee(email, null, null))
                    .toList();
        }

        CalendarEvent event = new CalendarEvent(
                null, summary, null, location, startDto, endDto, null, attendees, null);

        try {
            CalendarEvent created = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.createEvent(config.calendarId(), event));
            return "Event created: " + formatEvent(created);
        } catch (Exception e) {
            Log.errorf(e, "Failed to create Google Calendar event");
            return diagnoseFailure("create calendar event", e);
        }
    }

    @Tool("Change an existing Google Calendar event. Call listEvents first for the event id and its current title. "
            + "Give only the fields to change; attendeeEmails replaces the whole guest list, and moving an event "
            + "needs both startTime and endTime. Guests are notified. The owner approves the change and sees "
            + "currentTitle, and the call is refused when it is not the title of that event.")
    public String updateEvent(
            @P("The event id shown by listEvents") String eventId,
            @P("The event's current title exactly as listEvents shows it") String currentTitle,
            @P(value = "New event title", required = false) String newTitle,
            @P(value = "New start time in ISO-8601 format, e.g. 2026-05-10T14:00:00-03:00", required = false) String startTime,
            @P(value = "New end time in ISO-8601 format, e.g. 2026-05-10T15:00:00-03:00", required = false) String endTime,
            @P(value = "New location", required = false) String location,
            @P(value = "New description", required = false) String description,
            @P(value = "The complete new list of guest email addresses", required = false) List<String> attendeeEmails) {

        boolean hasGuests = attendeeEmails != null && !attendeeEmails.isEmpty();
        boolean moves = startTime != null || endTime != null;
        if (newTitle == null && location == null && description == null && !hasGuests && !moves) {
            return "Nothing to change: give at least one of newTitle, startTime and endTime, location, "
                    + "description or attendeeEmails.";
        }
        if (moves && (startTime == null || endTime == null)) {
            return "To move an event give both startTime and endTime.";
        }

        EventDateTime startDto = null;
        EventDateTime endDto = null;
        if (moves) {
            try {
                startDto = utc(startTime);
                endDto = utc(endTime);
            } catch (DateTimeParseException e) {
                return "Could not read the time: use ISO-8601 with an offset, e.g. 2026-05-10T14:00:00-03:00.";
            }
        }
        List<CalendarEventAttendee> guests = hasGuests
                ? attendeeEmails.stream().map(email -> new CalendarEventAttendee(email, null, null)).toList()
                : null;

        CalendarEventPatch patch = new CalendarEventPatch(newTitle, description, location, startDto, endDto, guests);
        List<String> changed = new ArrayList<>();
        if (newTitle != null) {
            changed.add("title");
        }
        if (moves) {
            changed.add("time");
        }
        if (location != null) {
            changed.add("location");
        }
        if (description != null) {
            changed.add("description");
        }
        if (hasGuests) {
            changed.add("guests");
        }

        try {
            String refusal = refusalFor(eventId, currentTitle, moves);
            if (refusal != null) {
                return refusal;
            }
            CalendarEvent updated = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.patchEvent(config.calendarId(), eventId, NOTIFY_GUESTS, patch));
            return "Event updated (changed: " + String.join(", ", changed) + "): " + formatEvent(updated);
        } catch (Exception e) {
            Log.errorf(e, "Failed to update Google Calendar event %s", eventId);
            return failureOrMissing(eventId, "update calendar event", e);
        }
    }

    @Tool("Cancel (delete) a Google Calendar event and notify its guests. Call listEvents first for the event id "
            + "and its current title. The owner approves the cancellation and sees currentTitle, and the call is "
            + "refused when it is not the title of that event.")
    public String deleteEvent(
            @P("The event id shown by listEvents") String eventId,
            @P("The event's current title exactly as listEvents shows it") String currentTitle) {

        try {
            CalendarEvent existing = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.getEvent(config.calendarId(), eventId));
            String refusal = refusalFor(existing, eventId, currentTitle, false);
            if (refusal != null) {
                return refusal;
            }
            apiExecutor.executeWithAuthRetry(() -> {
                calendarClient.deleteEvent(config.calendarId(), eventId, NOTIFY_GUESTS);
                return null;
            });
            return "Event deleted and guests notified: " + formatEvent(existing);
        } catch (Exception e) {
            Log.errorf(e, "Failed to delete Google Calendar event %s", eventId);
            return failureOrMissing(eventId, "delete calendar event", e);
        }
    }

    @Tool("Check calendar availability for a given date. Returns busy time ranges so you can find free slots.")
    public String checkAvailability(
            @P("Date to check in ISO-8601 format, e.g. 2026-05-10") String date) {

        LocalDate parsedDate = LocalDate.parse(date);

        String timeMin = parsedDate.atTime(OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC)).format(RFC3339);
        String timeMax = parsedDate.atTime(OffsetTime.of(23, 59, 59, 0, ZoneOffset.UTC)).format(RFC3339);

        FreeBusyRequest request = new FreeBusyRequest(
                timeMin, timeMax,
                List.of(new FreeBusyRequest.FreeBusyItem(config.calendarId())));

        try {
            FreeBusyResponse response = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.queryFreeBusy(request));
            FreeBusyResponse.FreeBusyCalendar calendar = response.calendars().get(config.calendarId());

            if (calendar == null || calendar.busy().isEmpty()) {
                return "No busy slots on " + parsedDate + ". Full day available.";
            }

            StringBuilder sb = new StringBuilder("Busy slots on " + parsedDate + ":\n");
            for (FreeBusyResponse.TimeRange range : calendar.busy()) {
                sb.append("- ").append(timezoneNormalizer.displayLocal(range.start()))
                        .append(" to ").append(timezoneNormalizer.displayLocal(range.end())).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            Log.errorf(e, "Failed to check calendar availability");
            return diagnoseFailure("check calendar availability", e);
        }
    }

    @Tool("Find available focus time slots of at least 2 hours on a given date. Uses freeBusy data and working hours to suggest gaps.")
    public String suggestFocusTime(
            @P("Date to check in ISO-8601 format, e.g. 2026-05-10") String date) {

        LocalDate parsedDate = LocalDate.parse(date);

        OffsetDateTime dayStart = parsedDate.atTime(config.workDayStart(), 0, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime dayEnd = parsedDate.atTime(config.workDayEnd(), 0, 0).atOffset(ZoneOffset.UTC);
        Duration minSlot = Duration.ofHours(config.focusTimeHours());

        FreeBusyRequest request = new FreeBusyRequest(
                dayStart.format(RFC3339),
                dayEnd.format(RFC3339),
                List.of(new FreeBusyRequest.FreeBusyItem(config.calendarId())));

        try {
            FreeBusyResponse response = apiExecutor.executeWithAuthRetry(() ->
                    calendarClient.queryFreeBusy(request));
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
            for (int i = 0; i < boundaries.size() - 1; i += 2) {
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
            return "No focus time slots of " + config.focusTimeHours() + "h+ available on " + parsedDate
                    + " within working hours (" + config.workDayStart() + ":00-" + config.workDayEnd() + ":00).";
        }

        return "Focus time slots on " + parsedDate + ":\n" + String.join("\n", focusSlots);
        } catch (Exception e) {
            Log.errorf(e, "Failed to suggest focus time");
            return diagnoseFailure("suggest focus time", e);
        }
    }

    private String refusalFor(String eventId, String currentTitle, boolean moves) throws Exception {
        CalendarEvent existing = apiExecutor.executeWithAuthRetry(() ->
                calendarClient.getEvent(config.calendarId(), eventId));
        return refusalFor(existing, eventId, currentTitle, moves);
    }

    private String refusalFor(CalendarEvent existing, String eventId, String currentTitle, boolean moves) {
        if ("cancelled".equals(existing.status())) {
            return noEvent(eventId);
        }
        String actual = existing.summary() != null ? existing.summary() : "";
        if (currentTitle == null || !actual.strip().equalsIgnoreCase(currentTitle.strip())) {
            return "Refused: event " + eventId + " is titled '" + actual + "', not '" + currentTitle
                    + "'. Nothing was changed; call listEvents to get the right id.";
        }
        if (moves && existing.start() != null && existing.start().dateTime() == null) {
            return "Event " + eventId + " is an all-day event; moving it to a time is not supported here.";
        }
        return null;
    }

    private String failureOrMissing(String eventId, String operation, Exception e) {
        if (e instanceof WebApplicationException web
                && (web.getResponse().getStatus() == 404 || web.getResponse().getStatus() == 410)) {
            return noEvent(eventId);
        }
        return diagnoseFailure(operation, e);
    }

    private String noEvent(String eventId) {
        return "No event with id " + eventId + " in calendar " + config.calendarId()
                + ", or it was already deleted. Call listEvents for the current ids.";
    }

    private static EventDateTime utc(String isoTime) {
        return new EventDateTime(
                OffsetDateTime.parse(isoTime).withOffsetSameInstant(ZoneOffset.UTC).format(RFC3339), null, null);
    }

    private String diagnoseFailure(String operation, Exception e) {
        return GoogleApiDiagnostics.diagnose(operation, e);
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
        if (event.id() != null) {
            sb.append(" | ID: ").append(event.id());
        }
        if (event.htmlLink() != null) {
            sb.append(" | Link: ").append(event.htmlLink());
        }
        return sb.toString();
    }
}
