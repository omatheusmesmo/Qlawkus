package dev.omatheusmesmo.qlawkus.task;

import java.util.UUID;

/** Requests a best-effort failure notice at the scheduled task's authorized destination. */
public record ScheduledTaskFailedEvent(String taskId, UUID runId, TaskDestination destination) {}
