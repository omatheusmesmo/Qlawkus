package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.TemplateData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * An instant as the console renders it through {@code console/time.html}: the ISO value for the
 * {@code datetime} attribute, the exact reading in the zone the page names (also the text a browser without
 * script shows), and that zone's id.
 *
 * @param iso   ISO-8601 instant
 * @param local reading in the page's zone, {@code uuuu-MM-dd HH:mm:ss}
 * @param zone  id of the zone {@code local} is in
 */
@TemplateData
public record TimeLabel(String iso, String local, String zone) {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    /** The label for {@code instant} in {@code zone}, or null when there is no instant. */
    public static TimeLabel of(Instant instant, ZoneId zone) {
        if (instant == null) {
            return null;
        }
        return new TimeLabel(instant.toString(), LOCAL.format(instant.atZone(zone)), zone.getId());
    }

    /** The label for an ISO-8601 instant, or null when the text is absent or not an instant. */
    public static TimeLabel parse(String iso, ZoneId zone) {
        if (iso == null) {
            return null;
        }
        try {
            return of(Instant.parse(iso), zone);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
