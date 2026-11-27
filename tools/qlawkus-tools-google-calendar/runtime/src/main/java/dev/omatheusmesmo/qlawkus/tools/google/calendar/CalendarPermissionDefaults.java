package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.omatheusmesmo.qlawkus.permission.PermissionDefaults;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Suggests {@code ask} for the {@code update} and {@code delete} cells of the {@code calendar} group
 * while {@code qlawkus.google.calendar.require-approval} is true, since both notify the guests. It is a
 * default: an operator's own matrix entry for the group or the operation wins over it. Reading and
 * creating are never suggested.
 */
@ApplicationScoped
public class CalendarPermissionDefaults implements PermissionDefaults {

    static final String GROUP = "calendar";

    private final GoogleCalendarConfig config;

    @Inject
    public CalendarPermissionDefaults(GoogleCalendarConfig config) {
        this.config = config;
    }

    @Override
    public List<Cell> cells() {
        if (!config.enabled() || !config.requireApproval()) {
            return List.of();
        }
        return List.of(new Cell(GROUP, AccessClass.UPDATE, PermissionLevel.ASK),
                new Cell(GROUP, AccessClass.DELETE, PermissionLevel.ASK));
    }
}
