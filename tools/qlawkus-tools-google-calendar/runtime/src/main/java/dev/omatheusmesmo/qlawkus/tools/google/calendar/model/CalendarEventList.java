package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarEventList(
        @JsonProperty("items") List<CalendarEvent> items,
        @JsonProperty("nextPageToken") String nextPageToken,
        @JsonProperty("summary") String summary) {

    public CalendarEventList {
        items = items != null ? items : List.of();
    }
}
