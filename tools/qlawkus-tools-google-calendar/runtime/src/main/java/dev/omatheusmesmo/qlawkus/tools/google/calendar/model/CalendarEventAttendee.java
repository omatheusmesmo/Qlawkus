package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarEventAttendee(
        @JsonProperty("email") String email,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("responseStatus") String responseStatus) {
}
