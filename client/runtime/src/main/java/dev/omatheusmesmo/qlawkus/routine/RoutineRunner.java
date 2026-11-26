package dev.omatheusmesmo.qlawkus.routine;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.cognition.JobLog;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Runs one routine: asks the agent for the report on the batch workload, bound to the owner's most
 * recent chat so the tools of the turn and the owner's reply both land in that conversation, and
 * delivers the answer there. A routine never fails silently: a run with nothing to say, or one that
 * throws, tells the owner so, naming the exception class and never its message. A failed run is not
 * retried.
 */
@ApplicationScoped
public class RoutineRunner {

    @Inject
    RoutinesConfig config;

    @Inject
    AgentConfig agentConfig;

    @Inject
    AgentRunner agent;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    OwnerChatDirectory owners;

    @Inject
    Event<WorkflowResultReadyEvent> results;

    @Inject
    AgentMeters meters;

    /** The scheduled firing: does nothing while the owner has the routine switched off. */
    public RoutineOutcome runScheduled(Routine routine) {
        if (!enabled(routine)) {
            return RoutineOutcome.DISABLED;
        }
        return runNow(routine);
    }

    /** Runs the routine whether or not it is switched on, so the owner can see what it says. */
    public RoutineOutcome runNow(Routine routine) {
        long startedAt = System.nanoTime();
        JobLog.Run run = JobLog.start(routine.job());
        RoutineOutcome outcome = execute(routine, run);
        run.count("outcome", outcome.name().toLowerCase()).finish();
        meters.job(routine.job(), Duration.ofNanos(System.nanoTime() - startedAt),
                outcome == RoutineOutcome.SENT ? 1 : 0, outcome != RoutineOutcome.FAILED);
        return outcome;
    }

    private RoutineOutcome execute(Routine routine, JobLog.Run run) {
        Optional<OwnerChat> chat = owners.mostRecent();
        if (chat.isEmpty()) {
            return RoutineOutcome.NO_DESTINATION;
        }
        OwnerChat destination = chat.get();
        String memoryId = agentConfig.sharedContext().enabled() ? ConversationId.SHARED
                : destination.providerId() + ":" + destination.chatId();
        ManagedContext requests = Arc.container().requestContext();
        boolean activated = !requests.isActive();
        if (activated) {
            requests.activate();
        }
        delivery.set(memoryId, destination.providerId(), destination.chatId());
        try {
            String reply = WorkloadContext.callAs(WorkloadContext.BATCH,
                    () -> agent.chat(memoryId, prompt(routine)));
            if (reply == null || reply.isBlank()) {
                send(destination, "Your " + routine.title() + " ran, but I came back with nothing to report. "
                        + "Ask me for it and I will try again.");
                return RoutineOutcome.NO_REPORT;
            }
            send(destination, reply);
            return RoutineOutcome.SENT;
        } catch (RuntimeException e) {
            run.fail(e);
            send(destination, "I could not produce your " + routine.title() + " (" + e.getClass().getSimpleName()
                    + "). Ask me for it and I will try again.");
            return RoutineOutcome.FAILED;
        } finally {
            delivery.clear();
            if (activated) {
                requests.terminate();
            }
        }
    }

    private void send(OwnerChat destination, String text) {
        results.fireAsync(new WorkflowResultReadyEvent(destination.providerId(), destination.chatId(), text));
    }

    private boolean enabled(Routine routine) {
        return routine == Routine.WEEKLY ? config.weekly().enabled() : config.morning().enabled();
    }

    private String prompt(Routine routine) {
        ZoneId zone = ZoneId.of(routine == Routine.WEEKLY ? config.weekly().timeZone() : config.morning().timeZone());
        Optional<String> custom = routine == Routine.WEEKLY ? config.weekly().prompt() : config.morning().prompt();
        return RoutinePrompts.build(routine, ZonedDateTime.now(zone), custom, config.morning().abandonedAfterDays());
    }
}
