package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The body of a Calendar patch: a field left null is omitted, so Google keeps what the event has.
 * {@link CalendarEvent} cannot serve here because it turns a missing guest list into an empty one,
 * which a patch would read as "remove every guest".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalendarEventPatch(
        @JsonProperty("summary") String summary,
        @JsonProperty("description") String description,
        @JsonProperty("location") String location,
        @JsonProperty("start") EventDateTime start,
        @JsonProperty("end") EventDateTime end,
        @JsonProperty("attendees") List<CalendarEventAttendee> attendees) {
}
