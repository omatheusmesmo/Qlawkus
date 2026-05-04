package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class TimezoneNormalizer {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ZoneId userZone;

    TimezoneNormalizer(GoogleCalendarConfig config) {
        this.userZone = ZoneId.of(config.timezone());
    }

    public OffsetDateTime toUtc(OffsetDateTime localTime) {
        return localTime.atZoneSameInstant(userZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
    }

    public String displayLocal(OffsetDateTime utcTime) {
        return utcTime.atZoneSameInstant(userZone).format(DISPLAY);
    }

    public String displayLocal(String utcIso) {
        return displayLocal(OffsetDateTime.parse(utcIso));
    }

    public LocalDate todayLocal() {
        return LocalDate.now(userZone);
    }

    public ZoneId userZone() {
        return userZone;
    }
}
