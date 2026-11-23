package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.TemplateData;

import java.time.Instant;

/**
 * One line of the Upcoming card: what will run, whether the agent or a background job owns it, and
 * when. {@code kind} is {@code job} or {@code task} and only drives the label next to the time.
 */
@TemplateData
public record UpcomingRun(String label, String kind, Instant at, String when) {

    public String kindLabel() {
        return "task".equals(kind) ? "Agent task" : "Background job";
    }
}
