package dev.omatheusmesmo.qlawkus.console.management;

/** One background job row rendered by the schedule page. */
public record ScheduledJobView(
        String identity,
        String label,
        String cronProperty,
        String currentCron,
        String effectiveSource,
        String shadowWarning,
        String triggerPath,
        String previousFireTime,
        String nextFireTime
) {
}
