package dev.omatheusmesmo.qlawkus.tools.google.calendar;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequirement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Puts the tools that change or cancel an existing event behind the owner's durable approval while
 * {@code qlawkus.google.calendar.require-approval} is true, since both notify the guests. Reading and
 * creating never wait.
 */
@ApplicationScoped
public class CalendarApprovalRequirement implements ApprovalRequirement {

    static final Set<String> GATED_TOOLS = Set.of("updateEvent", "deleteEvent");

    private final GoogleCalendarConfig config;

    @Inject
    public CalendarApprovalRequirement(GoogleCalendarConfig config) {
        this.config = config;
    }

    @Override
    public boolean requires(String toolName) {
        return config.enabled() && config.requireApproval() && GATED_TOOLS.contains(toolName);
    }
}
