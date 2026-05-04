package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEvent;
import dev.omatheusmesmo.qlawkus.tools.google.calendar.model.CalendarEventList;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    private String formatEvent(CalendarEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.summary() != null ? event.summary() : "(no title)");

        if (event.start() != null) {
            String start = event.start().dateTime() != null ? event.start().dateTime() : event.start().date();
            sb.append(" | Start: ").append(start);
        }
        if (event.end() != null) {
            String end = event.end().dateTime() != null ? event.end().dateTime() : event.end().date();
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
