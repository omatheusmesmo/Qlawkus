package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.tool.quarantine.ToolQuarantine;
import java.time.Instant;

/** A tool the agent is not offered right now and when it comes back on probation. */
public record QuarantinedToolView(String tool, Instant since, Instant until) {

    public static QuarantinedToolView of(ToolQuarantine.Entry entry) {
        return new QuarantinedToolView(entry.tool(), entry.since(), entry.until());
    }
}
