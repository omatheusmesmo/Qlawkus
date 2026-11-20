package dev.omatheusmesmo.qlawkus.task;

import java.time.Instant;

public record TaskView(ScheduledTask task, Instant nextFire) {
}
