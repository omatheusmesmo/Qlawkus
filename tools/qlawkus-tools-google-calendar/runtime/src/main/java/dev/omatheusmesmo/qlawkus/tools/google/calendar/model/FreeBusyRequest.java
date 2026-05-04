package dev.omatheusmesmo.qlawkus.tools.google.calendar.model;

import java.util.List;

public record FreeBusyRequest(
        String timeMin,
        String timeMax,
        List<FreeBusyItem> items) {

    public record FreeBusyItem(
            String id) {
    }
}
