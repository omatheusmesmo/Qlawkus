package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.permission.PermissionDefaults;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarPermissionDefaultsTest {

    @Test
    void changingAndCancellingAnEventAskTheOwnerWhileTheSwitchIsOn() {
        List<PermissionDefaults.Cell> cells = new CalendarPermissionDefaults(config(true, true)).cells();

        assertEquals(List.of(
                new PermissionDefaults.Cell("calendar", AccessClass.UPDATE, PermissionLevel.ASK),
                new PermissionDefaults.Cell("calendar", AccessClass.DELETE, PermissionLevel.ASK)), cells);
    }

    @Test
    void readingAndCreatingAreNeverAskedAbout() {
        List<PermissionDefaults.Cell> cells = new CalendarPermissionDefaults(config(true, true)).cells();

        assertTrue(cells.stream().noneMatch(cell -> cell.operation() == AccessClass.READ
                || cell.operation() == AccessClass.CREATE));
    }

    @Test
    void theOperatorCanSwitchTheDefaultOffOrDisableTheExtension() {
        assertTrue(new CalendarPermissionDefaults(config(true, false)).cells().isEmpty());
        assertTrue(new CalendarPermissionDefaults(config(false, true)).cells().isEmpty());
    }

    private static GoogleCalendarConfig config(boolean enabled, boolean requireApproval) {
        return new GoogleCalendarConfig() {
            public boolean enabled() { return enabled; }
            public String calendarId() { return "primary"; }
            public int defaultDaysRange() { return 7; }
            public int maxResults() { return 25; }
            public int focusTimeHours() { return 2; }
            public int workDayStart() { return 8; }
            public int workDayEnd() { return 18; }
            public String timezone() { return "America/Sao_Paulo"; }
            public boolean requireApproval() { return requireApproval; }
        };
    }
}
