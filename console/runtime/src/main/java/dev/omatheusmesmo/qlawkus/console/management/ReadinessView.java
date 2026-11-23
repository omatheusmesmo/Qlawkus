package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.TemplateData;

/**
 * One readiness check as the overview's Health card shows it. {@code status} is {@code UP},
 * {@code DEGRADED} (up, but naming a store that is not fully working) or {@code DOWN}; {@code reason}
 * is a single redacted line, or {@code null} when the check is healthy.
 */
@TemplateData
public record ReadinessView(String name, String label, String status, String reason) {

    public String dot() {
        return switch (status) {
            case ReadinessViews.UP -> "up";
            case ReadinessViews.DEGRADED -> "warn";
            default -> "down";
        };
    }

    public String statusLabel() {
        return status.toLowerCase();
    }
}
