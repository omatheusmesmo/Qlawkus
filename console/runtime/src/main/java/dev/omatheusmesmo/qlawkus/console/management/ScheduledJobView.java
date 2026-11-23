package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.TemplateData;

/** One background job row rendered by the schedule page. */
@TemplateData
public record ScheduledJobView(
        String identity,
        String label,
        String cronProperty,
        String currentCron,
        String effectiveSource,
        String sourceLabel,
        String shadowWarning,
        String triggerPath,
        String previousFireTime,
        String nextFireTime
) {
}
