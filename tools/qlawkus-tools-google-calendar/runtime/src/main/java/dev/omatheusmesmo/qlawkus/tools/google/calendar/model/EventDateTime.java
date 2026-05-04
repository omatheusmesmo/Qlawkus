package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDateTime(
        @JsonProperty("dateTime") String dateTime,
        @JsonProperty("date") String date,
        @JsonProperty("timeZone") String timeZone) {
}
