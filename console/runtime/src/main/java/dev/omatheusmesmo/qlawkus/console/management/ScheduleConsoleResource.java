package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.config.RuntimeToggleConfig;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;

import java.time.Instant;
import java.util.List;

/**
 * The scheduling page: one row per background job, sourced from the live {@link Scheduler}
 * (cron, previous/next fire time) rather than a hand-maintained list. Cron is always
 * {@code RUN_TIME} (the only phase {@code @Scheduled} can read), so editing it is driven from the
 * page by HTMX against the same {@code /api/admin/runtime-toggles} endpoint the config editor
 * uses; triggering a job now hits its existing {@code /api/admin/*} endpoint.
 */
@Path("/console/schedule")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class ScheduleConsoleResource {

    private record JobDescriptor(String identity, String label, String cronProperty, String triggerPath) {
    }

    private static final List<JobDescriptor> JOBS = List.of(
            new JobDescriptor("memory-review", "Memory review (dedup facts)",
                    "qlawkus.memory-review.cron", "/api/admin/memory/review"),
            new JobDescriptor("memory-curation", "Memory curation (fold into profile)",
                    "qlawkus.memory-curation.cron", "/api/admin/memory/curate"),
            new JobDescriptor("episodic-consolidator", "Episodic consolidation (daily journal)",
                    "qlawkus.consolidator.cron", "/api/admin/memory/consolidate"),
            new JobDescriptor("skill-curation", "Skill curation (dedup skills)",
                    "qlawkus.skills.curation.cron", "/api/admin/skills/curate"),
            new JobDescriptor("skill-lifecycle", "Skill lifecycle (stale/archive sweep)",
                    "qlawkus.skills.lifecycle.cron", "/api/admin/skills/lifecycle")
    );

    private final Template scheduleTemplate;
    private final Scheduler scheduler;
    private final RuntimeToggleConfig runtimeToggleConfig;
    private final Config mpConfig;

    public ScheduleConsoleResource(@Location("console/schedule.html") Template scheduleTemplate,
                                    Scheduler scheduler,
                                    RuntimeToggleConfig runtimeToggleConfig) {
        this.scheduleTemplate = scheduleTemplate;
        this.scheduler = scheduler;
        this.runtimeToggleConfig = runtimeToggleConfig;
        this.mpConfig = ConfigProvider.getConfig();
    }

    @GET
    public TemplateInstance page() {
        List<ScheduledJobView> jobs = JOBS.stream().map(this::toView).toList();
        return scheduleTemplate.data("active", "schedule").data("jobs", jobs);
    }

    private ScheduledJobView toView(JobDescriptor job) {
        ConfigValue value = mpConfig.getConfigValue(job.cronProperty());
        Trigger trigger = scheduler.getScheduledJob(job.identity());
        return new ScheduledJobView(
                job.identity(),
                job.label(),
                job.cronProperty(),
                value.getValue(),
                value.getSourceName(),
                shadowWarning(value),
                job.triggerPath(),
                formatInstant(trigger == null ? null : trigger.getPreviousFireTime()),
                formatInstant(trigger == null ? null : trigger.getNextFireTime()));
    }

    private String shadowWarning(ConfigValue value) {
        if (value.getSourceName() == null || value.getSourceOrdinal() <= runtimeToggleConfig.overrideOrdinal()) {
            return null;
        }
        return "Currently set by %s (ordinal %d); a saved override here will not take effect until that is removed."
                .formatted(value.getSourceName(), value.getSourceOrdinal());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
