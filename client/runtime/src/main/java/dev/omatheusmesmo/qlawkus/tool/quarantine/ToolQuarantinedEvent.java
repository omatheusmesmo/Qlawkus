package dev.omatheusmesmo.qlawkus.tool.quarantine;

import java.time.Instant;

/** Fired when a tool is taken out of the catalog; {@code until} is when it comes back on probation. */
public record ToolQuarantinedEvent(String tool, int failures, Instant until) {
}
