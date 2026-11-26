package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalendarApprovalRequirementTest {

    @Test
    void changingOrCancellingAnEventWaitsForTheOwner() {
        CalendarApprovalRequirement requirement = new CalendarApprovalRequirement(config(true));

        assertTrue(requirement.requires("updateEvent"));
        assertTrue(requirement.requires("deleteEvent"));
    }

    @Test
    void readingAndCreatingNeverWait() {
        CalendarApprovalRequirement requirement = new CalendarApprovalRequirement(config(true));

        assertFalse(requirement.requires("listEvents"));
        assertFalse(requirement.requires("createEvent"));
        assertFalse(requirement.requires("checkAvailability"));
    }

    @Test
    void theOperatorCanSwitchTheGateOff() {
        CalendarApprovalRequirement requirement = new CalendarApprovalRequirement(config(false));

        assertFalse(requirement.requires("deleteEvent"));
    }

    @Test
    void aToolOfAnotherExtensionIsNotClaimed() {
        assertFalse(new CalendarApprovalRequirement(config(true)).requires("trashEmail"));
    }

    private static GoogleCalendarConfig config(boolean requireApproval) {
        return new GoogleCalendarConfig() {
            public boolean enabled() { return true; }
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
