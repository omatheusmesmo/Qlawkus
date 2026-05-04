package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarEvent(
        @JsonProperty("id") String id,
        @JsonProperty("summary") String summary,
        @JsonProperty("description") String description,
        @JsonProperty("location") String location,
        @JsonProperty("start") EventDateTime start,
        @JsonProperty("end") EventDateTime end,
        @JsonProperty("status") String status,
        @JsonProperty("attendees") List<CalendarEventAttendee> attendees) {

    public CalendarEvent {
        attendees = attendees != null ? attendees : List.of();
    }
}
