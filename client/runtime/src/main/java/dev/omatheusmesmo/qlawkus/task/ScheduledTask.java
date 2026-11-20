package dev.omatheusmesmo.qlawkus.task;

import java.time.Instant;

public record ScheduledTask(
        String id,
        String memoryId,
        TaskDestination destination,
        String task,
        String cron,
        boolean oneShot,
        Instant createdAt
) {
}
