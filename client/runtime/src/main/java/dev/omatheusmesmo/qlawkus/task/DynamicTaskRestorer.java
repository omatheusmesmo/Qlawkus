package dev.omatheusmesmo.qlawkus.task;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Restores the stored agent tasks at boot. It is an eager bean in the application phase, so it runs
 * after the instance lock has been taken: a refused second instance never restores, and so never
 * writes a missed or invalid state over the owner's records.
 *
 * <p>A failed restore is logged and boot carries on, because the tasks stay stored and the next start
 * tries again. It publishes every outcome, zero included, so each series exists from boot.
 */
@Startup
@ApplicationScoped
public class DynamicTaskRestorer {

    private final DynamicTaskScheduler tasks;
    private final AgentMeters meters;

    @Inject
    public DynamicTaskRestorer(DynamicTaskScheduler tasks, AgentMeters meters) {
        this.tasks = tasks;
        this.meters = meters;
    }

    @PostConstruct
    void restore() {
        TaskRestoreReport report;
        try {
            report = tasks.restore();
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not restore the agent's scheduled tasks; they stay stored and boot continues");
            report = TaskRestoreReport.NONE;
        }
        meters.taskRestore(AgentMeters.TASK_OUTCOME_RESTORED, report.restored());
        meters.taskRestore(AgentMeters.TASK_OUTCOME_MISSED, report.missed());
        meters.taskRestore(AgentMeters.TASK_OUTCOME_INVALID, report.invalid());
        meters.taskRestore(AgentMeters.TASK_OUTCOME_HELD, report.held());
        Log.infof("Restored agent tasks: %d registered, %d missed, %d invalid, %d held by the cap",
                report.restored(), report.missed(), report.invalid(), report.held());
    }
}
