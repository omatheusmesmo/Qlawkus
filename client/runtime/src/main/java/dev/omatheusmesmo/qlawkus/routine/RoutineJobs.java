package dev.omatheusmesmo.qlawkus.routine;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The two cron triggers. Both are always registered, so the next fire time is visible to the
 * scheduler, and {@link RoutineRunner#runScheduled} decides on each firing whether the routine is on.
 */
@ApplicationScoped
public class RoutineJobs {

    @Inject
    RoutineRunner runner;

    @Scheduled(identity = "routine-weekly", concurrentExecution = ConcurrentExecution.SKIP,
            cron = "{qlawkus.routines.weekly.cron}", timeZone = "{qlawkus.routines.weekly.time-zone}")
    void weekly() {
        runner.runScheduled(Routine.WEEKLY);
    }

    @Scheduled(identity = "routine-morning", concurrentExecution = ConcurrentExecution.SKIP,
            cron = "{qlawkus.routines.morning.cron}", timeZone = "{qlawkus.routines.morning.time-zone}")
    void morning() {
        runner.runScheduled(Routine.MORNING);
    }
}
