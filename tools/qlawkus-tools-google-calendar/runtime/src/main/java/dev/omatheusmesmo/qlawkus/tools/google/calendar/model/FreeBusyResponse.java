package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FreeBusyResponse(
        Map<String, FreeBusyCalendar> calendars) {

    public FreeBusyResponse {
        calendars = calendars != null ? calendars : Map.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FreeBusyCalendar(
            List<TimeRange> busy) {

        public FreeBusyCalendar {
            busy = busy != null ? busy : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeRange(
            String start,
            String end) {
    }
}
