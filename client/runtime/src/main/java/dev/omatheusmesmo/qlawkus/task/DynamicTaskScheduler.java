package dev.omatheusmesmo.qlawkus.task;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tasks the agent schedules at the owner's request, kept apart from the application's own
 * {@code @Scheduled} jobs. A task is an in-memory record plus a Quartz job registered with the
 * scheduler under a namespaced UUID, so cancelling one can never touch a static job.
 *
 * <p>A run replays the task into the conversation that scheduled it, through {@link AgentRunner}, with the
 * destination restored as the delivery context, and hands the reply to {@link WorkflowResultReadyEvent}
 * for whichever messaging channel the destination names. A one-shot task removes itself before the model
 * is called, so a run that fails still leaves nothing scheduled. Everything here is volatile: a restart,
 * redeploy or crash loses the tasks, and a run missed while the agent was down is not caught up.
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
    private final ZoneId zone;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();

    @Inject
    public DynamicTaskScheduler(Scheduler scheduler, AgentRunner agentRunner, AgentDeliveryContext delivery,
            Event<WorkflowResultReadyEvent> resultEvent, AgentConfig config) {
        this.scheduler = scheduler;
        this.agentRunner = agentRunner;
        this.delivery = delivery;
        this.resultEvent = resultEvent;
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
                isOneShot(cron), Instant.now());
        tasks.put(id, scheduled);
        Instant nextFire = register(scheduled);
        Log.infof("Scheduled agent task %s oneShot=%s", id, scheduled.oneShot());
        return new TaskView(scheduled, nextFire);
    }

    public List<TaskView> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(ScheduledTask::createdAt))
                .map(task -> new TaskView(task, nextFire(task.id())))
                .toList();
    }

    public synchronized boolean cancel(String id) {
        if (id == null || !id.startsWith(ID_PREFIX) || tasks.remove(id) == null) {
            return false;
        }
        scheduler.unscheduleJob(id);
        Log.infof("Cancelled agent task %s", id);
        return true;
    }

    public void fire(String id) {
        ScheduledTask task = tasks.get(id);
        if (task == null) {
            Log.debugf("Agent task %s is gone, nothing to run", id);
            return;
        }
        if (task.oneShot()) {
            retire(id);
        }
        run(task);
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

    private Instant register(ScheduledTask task) {
        Trigger trigger;
        try {
            trigger = scheduler.newJob(task.id())
                    .setCron(task.cron())
                    .setTimeZone(zone.getId())
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setTask(execution -> fire(task.id()))
                    .schedule();
        } catch (RuntimeException e) {
            tasks.remove(task.id());
            throw new IllegalArgumentException("cron is not a valid schedule: " + task.cron(), e);
        }
        Instant nextFire = trigger == null ? null : trigger.getNextFireTime();
        if (nextFire == null) {
            tasks.remove(task.id());
            scheduler.unscheduleJob(task.id());
            throw new IllegalArgumentException("cron never fires again: " + task.cron());
        }
        return nextFire;
    }

    private Instant nextFire(String id) {
        Trigger trigger = scheduler.getScheduledJob(id);
        return trigger == null ? null : trigger.getNextFireTime();
    }

    private void retire(String id) {
        tasks.remove(id);
        scheduler.unscheduleJob(id);
    }

    private void run(ScheduledTask task) {
        TaskDestination destination = task.destination();
        ManagedContext requests = Arc.container().requestContext();
        boolean activated = !requests.isActive();
        if (activated) {
            requests.activate();
        }
        delivery.set(task.memoryId(), destination.providerId(), destination.chatId());
        try {
            String response = WorkloadContext.callAs(WorkloadContext.INTERACTIVE,
                    () -> agentRunner.chat(task.memoryId(), prompt(task)));
            if (response == null || response.isBlank()) {
                Log.infof("Agent task %s produced no reply to deliver", task.id());
                return;
            }
            resultEvent.fireAsync(new WorkflowResultReadyEvent(destination.providerId(), destination.chatId(),
                    response));
            Log.infof("Agent task %s delivered a reply", task.id());
        } catch (RuntimeException e) {
            Log.errorf(e, "Agent task %s failed", task.id());
        } finally {
            delivery.clear();
            if (activated) {
                requests.terminate();
            }
        }
    }

    private String prompt(ScheduledTask task) {
        String now = ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return "A task the owner asked you to schedule is due now (" + task.id() + ", it is " + now + "). "
                + "What the owner asked for: " + task.task() + "\n"
                + "Do it now, using your tools if they help. Reply with the message to send the owner, "
                + "or with an empty reply if there is nothing to send.";
    }
}
