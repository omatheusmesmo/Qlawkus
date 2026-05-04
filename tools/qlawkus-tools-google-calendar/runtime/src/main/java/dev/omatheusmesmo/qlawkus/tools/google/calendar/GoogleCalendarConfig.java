package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.calendar")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GoogleCalendarConfig {

    /**
     * Whether the Google Calendar tool is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Calendar identifier. Use {@code primary} for the authenticated user's main calendar,
     * or an email address for a specific calendar.
     */
    @WithDefault("primary")
    String calendarId();

    /**
     * Default number of days to look ahead when listing events.
     */
    @WithDefault("7")
    int defaultDaysRange();

    /**
     * Maximum number of events returned per request.
     */
    @WithDefault("25")
    int maxResults();

    /** Minimum duration (in hours) for a focus time slot. */
    @WithDefault("2")
    int focusTimeHours();

    /** Start of working hours (hour of day, 0-23). Focus slots are only suggested within this range. */
    @WithDefault("8")
    int workDayStart();

    /** End of working hours (hour of day, 0-23). */
    @WithDefault("18")
    int workDayEnd();
}
