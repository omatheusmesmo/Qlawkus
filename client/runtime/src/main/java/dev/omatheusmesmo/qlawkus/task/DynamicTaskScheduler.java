package dev.omatheusmesmo.qlawkus.task;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.cognition.JobLog;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent.Stage;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tasks the agent schedules at the owner's request, kept apart from the application's own
 * {@code @Scheduled} jobs. A task is a record plus a Quartz job registered under a namespaced UUID, so
 * cancelling one can never touch a static job.
 *
 * <p>Every task is written to a {@link ScheduledTaskStore} before it is reported as scheduled, and
 * removed from it when cancelled or when a one-time task retires. {@link #restore()} registers the
 * stored tasks again after a restart. A one-time task whose time passed while the agent was down is
 * marked {@link TaskStatus#MISSED} and never run; a schedule the scheduler cannot run is marked
 * {@link TaskStatus#INVALID}. Both stay listed until cancelled, so the owner can be told.
 *
 * <p>A run replays the task into the conversation that scheduled it, through {@link AgentRunner}, with the
 * destination restored as the delivery context, and hands the reply to {@link WorkflowResultReadyEvent}
 * for whichever messaging channel the destination names. A one-time task removes its record before the
 * model is called, so a run that fails still leaves nothing scheduled. Delivery is best effort: a reply
 * that was not sent when the process died is not sent again.
 */
@ApplicationScoped
public class DynamicTaskScheduler {

    public static final String ID_PREFIX = "qlawkus-task-";
    public static final int MAX_TASKS = 20;
    public static final int MAX_TASK_LENGTH = 500;
    public static final int MAX_DESTINATION_LENGTH = 200;
    public static final int MAX_CRON_LENGTH = 100;
    public static final long MAX_MINUTES = 525_600L;

    private static final Pattern LITERAL_FIELD = Pattern.compile("\\d+");
    private static final Pattern LITERAL_YEAR = Pattern.compile("\\d{4}");
    private static final int CRON_FIELDS_WITH_YEAR = 7;

    private final Scheduler scheduler;
    private final AgentRunner agentRunner;
    private final AgentDeliveryContext delivery;
    private final Event<WorkflowResultReadyEvent> resultEvent;
    private final Event<ScheduledTaskFailedEvent> failedEvent;
    private final ScheduledTaskStore store;
    private final TaskAudit audit;
    private final ZoneId zone;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();

    @Inject
    public DynamicTaskScheduler(Scheduler scheduler, AgentRunner agentRunner, AgentDeliveryContext delivery,
            Event<WorkflowResultReadyEvent> resultEvent, Event<ScheduledTaskFailedEvent> failedEvent,
            AgentConfig config, ScheduledTaskStore store, TaskAudit audit) {
        this.scheduler = scheduler;
        this.agentRunner = agentRunner;
        this.delivery = delivery;
        this.resultEvent = resultEvent;
        this.failedEvent = failedEvent;
        this.store = store;
        this.audit = audit;
        this.zone = ZoneId.of(config.timezone());
    }

    public synchronized TaskView create(String memoryId, TaskDestination destination, String task, String cron) {
        requireText(memoryId, "conversation", MAX_DESTINATION_LENGTH);
        requireText(task, "task", MAX_TASK_LENGTH);
        requireText(cron, "cron", MAX_CRON_LENGTH);
        if (tasks.size() >= MAX_TASKS) {
            throw new IllegalStateException("at most " + MAX_TASKS + " scheduled tasks; cancel one first");
        }
        String id = ID_PREFIX + UUID.randomUUID();
        ScheduledTask scheduled = new ScheduledTask(id, memoryId, destination, task.strip(), cron.strip(),
                isOneShot(cron), Instant.now(), TaskStatus.ACTIVE);
        tasks.put(id, scheduled);
        Instant nextFire;
        try {
            nextFire = schedule(scheduled);
        } catch (IllegalArgumentException e) {
            tasks.remove(id);
            throw e;
        }
        try {
            store.save(scheduled);
        } catch (RuntimeException e) {
            tasks.remove(id);
            unschedule(id);
            removeRecordQuietly(id);
            throw new IllegalStateException("could not save the task, so it was not scheduled", e);
        }
        Log.infof("Scheduled agent task %s provider=%s chatId=%s cron=%s nextFire=%s oneShot=%s", id,
                destination.providerId(), destination.chatId(), scheduled.cron(), nextFire, scheduled.oneShot());
        record(scheduled, null, Stage.SCHEDULED, nextFire, null);
        return new TaskView(scheduled, nextFire);
    }

    public List<TaskView> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(ScheduledTask::createdAt))
                .map(task -> new TaskView(task, nextFire(task.id())))
                .toList();
    }

    public synchronized boolean cancel(String id) {
        if (id == null || !id.startsWith(ID_PREFIX) || !tasks.containsKey(id)) {
            return false;
        }
        try {
            store.delete(id);
        } catch (RuntimeException e) {
            throw new IllegalStateException("could not remove the task record, so the task stays scheduled", e);
        }
        ScheduledTask cancelled = tasks.remove(id);
        scheduler.unscheduleJob(id);
        if (cancelled != null) {
            record(cancelled, null, Stage.CANCELLED, null, null);
        }
        Log.infof("Cancelled agent task %s", id);
        return true;
    }

    public void fire(String id) {
        ScheduledTask task = tasks.get(id);
        if (task == null || task.status() != TaskStatus.ACTIVE) {
            Log.debugf("Agent task %s is not due to run, nothing to do", id);
            return;
        }
        if (task.oneShot()) {
            retire(id);
        }
        run(task);
    }

    /**
     * Registers every stored task again, once per id. A task already in the registry is left alone, and
     * the task cap is never exceeded: the rest stay stored and are counted as held.
     */
    public synchronized TaskRestoreReport restore() {
        int restored = 0;
        int missed = 0;
        int invalid = 0;
        int held = 0;
        for (ScheduledTask stored : store.loadAll()) {
            if (tasks.containsKey(stored.id())) {
                continue;
            }
            if (tasks.size() >= MAX_TASKS) {
                held++;
                continue;
            }
            ScheduledTask restoredTask = restoreOne(stored);
            tasks.put(restoredTask.id(), restoredTask);
            if (stored.status() == TaskStatus.ACTIVE) {
                switch (restoredTask.status()) {
                    case ACTIVE -> {
                        restored++;
                        record(restoredTask, null, Stage.RESTORED, nextFire(stored.id()), null);
                    }
                    case MISSED -> {
                        missed++;
                        record(restoredTask, null, Stage.MISSED, null, null);
                    }
                    case INVALID -> {
                        invalid++;
                        record(restoredTask, null, Stage.INVALID, null, null);
                    }
                }
            }
        }
        if (held > 0) {
            Log.warnf("%d stored agent task(s) held back by the cap of %d", held, MAX_TASKS);
        }
        return new TaskRestoreReport(restored, missed, invalid, held);
    }

    public String cronInMinutes(long minutes) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("inMinutes must be between 1 and " + MAX_MINUTES);
        }
        ZonedDateTime at = ZonedDateTime.now(zone).plusMinutes(minutes).truncatedTo(ChronoUnit.SECONDS);
        return String.format("%d %d %d %d %d ? %d", at.getSecond(), at.getMinute(), at.getHour(),
                at.getDayOfMonth(), at.getMonthValue(), at.getYear());
    }

    public ZoneId zone() {
        return zone;
    }

    /**
     * A cron with a year and literal fields for everything but the day of week fires exactly once. It is
     * the only shape the registry treats as one-shot, so a wildcard or a step always stays recurring.
     */
    public static boolean isOneShot(String cron) {
        String[] fields = cron.strip().split("\\s+");
        if (fields.length != CRON_FIELDS_WITH_YEAR || !"?".equals(fields[5])) {
            return false;
        }
        for (int index : new int[] {0, 1, 2, 3, 4}) {
            if (!LITERAL_FIELD.matcher(fields[index]).matches()) {
                return false;
            }
        }
        return LITERAL_YEAR.matcher(fields[6]).matches();
    }

    static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.strip().length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
    }

    private ScheduledTask restoreOne(ScheduledTask stored) {
        if (stored.status() != TaskStatus.ACTIVE) {
            return stored;
        }
        TaskStatus outcome = reschedule(stored);
        if (outcome == TaskStatus.ACTIVE) {
            return stored;
        }
        ScheduledTask changed = stored.withStatus(outcome);
        recordStatus(changed);
        if (outcome == TaskStatus.MISSED) {
            Log.infof("Agent task %s was due while the agent was down and was not run", stored.id());
        } else {
            Log.warnf("Agent task %s has a schedule that cannot run, so it is marked invalid", stored.id());
        }
        return changed;
    }

    private TaskStatus reschedule(ScheduledTask stored) {
        scheduler.unscheduleJob(stored.id());
        if (isOneShot(stored.cron())) {
            Optional<Instant> fireAt = oneShotFireTime(stored.cron());
            if (fireAt.isEmpty()) {
                return TaskStatus.INVALID;
            }
            if (!fireAt.get().isAfter(Instant.now())) {
                return TaskStatus.MISSED;
            }
        }
        try {
            schedule(stored);
            return TaskStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            return TaskStatus.INVALID;
        }
    }

    private Optional<Instant> oneShotFireTime(String cron) {
        String[] fields = cron.strip().split("\\s+");
        try {
            LocalDateTime at = LocalDateTime.of(
                    Integer.parseInt(fields[6]), Integer.parseInt(fields[4]), Integer.parseInt(fields[3]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[1]), Integer.parseInt(fields[0]));
            return Optional.of(at.atZone(zone).toInstant());
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void recordStatus(ScheduledTask task) {
        try {
            store.save(task);
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not record that agent task %s is %s", task.id(), task.status());
        }
    }

    private Instant schedule(ScheduledTask task) {
        Trigger trigger;
        try {
            trigger = scheduler.newJob(task.id())
                    .setCron(task.cron())
                    .setTimeZone(zone.getId())
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setTask(execution -> fire(task.id()))
                    .schedule();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cron is not a valid schedule: " + task.cron(), e);
        }
        Instant nextFire = trigger == null ? null : trigger.getNextFireTime();
        if (nextFire == null) {
            scheduler.unscheduleJob(task.id());
            throw new IllegalArgumentException("cron never fires again: " + task.cron());
        }
        return nextFire;
    }

    private void unschedule(String id) {
        scheduler.unscheduleJob(id);
    }

    private void removeRecordQuietly(String id) {
        try {
            store.delete(id);
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not remove the record of agent task %s after a failed save", id);
        }
    }

    private Instant nextFire(String id) {
        Trigger trigger = scheduler.getScheduledJob(id);
        return trigger == null ? null : trigger.getNextFireTime();
    }

    private void retire(String id) {
        tasks.remove(id);
        scheduler.unscheduleJob(id);
        try {
            store.delete(id);
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not remove the record of one-time agent task %s", id);
        }
    }

    private void run(ScheduledTask task) {
        UUID runId = UUID.randomUUID();
        TaskDestination destination = task.destination();
        ManagedContext requests = Arc.container().requestContext();
        boolean activated = !requests.isActive();
        if (activated) {
            requests.activate();
        }
        delivery.set(task.memoryId(), destination.providerId(), destination.chatId());
        JobLog.Run run = JobLog.start(task.id()).count("destination", destination.providerId());
        record(task, runId, Stage.STARTED, null, null);
        try {
            String response = TaskAudit.callAs(runId, () -> WorkloadContext.callAs(WorkloadContext.INTERACTIVE,
                    () -> agentRunner.chat(task.memoryId(), prompt(task))));
            if (response == null || response.isBlank()) {
                record(task, runId, Stage.NO_REPLY, null, null);
                run.count("outcome", "no-reply").finish();
                return;
            }
            record(task, runId, Stage.QUEUED, null, null);
            resultEvent.fireAsync(new WorkflowResultReadyEvent(destination.providerId(), destination.chatId(),
                    response, task.id(), runId));
            run.count("outcome", "queued").finish();
        } catch (RuntimeException e) {
            record(task, runId, Stage.EXECUTION_FAILED, null, e.getClass().getSimpleName());
            run.fail(e);
            failedEvent.fireAsync(new ScheduledTaskFailedEvent(task.id(), runId, destination));
        } finally {
            delivery.clear();
            if (activated) {
                requests.terminate();
            }
        }
    }

    private void record(ScheduledTask task, UUID runId, Stage stage, Instant nextFire, String errorType) {
        audit.record(new TaskAuditEvent(task.id(), runId, task.destination().providerId(), stage,
                nextFire, errorType, null, null, null));
    }

    private String prompt(ScheduledTask task) {
        String now = ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return "A task the owner asked you to schedule is due now (" + task.id() + ", it is " + now + "). "
                + "What the owner asked for: " + task.task() + "\n"
                + "Do it now, using your tools if they help. Reply with the message to send the owner, "
                + "or with an empty reply if there is nothing to send.";
    }
}
