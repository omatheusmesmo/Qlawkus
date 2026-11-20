package dev.omatheusmesmo.qlawkus.task;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;

@QlawTool
@ApplicationScoped
public class TaskSchedulerTool {

    private static final String DURABILITY = "Schedules survive a restart, redeploy or crash. A one-time task "
            + "that was due while the agent was down is reported as missed and is not run; it stays listed until "
            + "it is cancelled. Delivery is best effort.";

    private static final String MISSED_NOTE = "missed while the agent was down, not run: cancel it to remove it";
    private static final String INVALID_NOTE = "invalid schedule, it never runs: cancel it to remove it";

    private static final String REST_CHANNEL = "rest";

    private final DynamicTaskScheduler scheduler;
    private final AgentDeliveryContext delivery;

    @Inject
    public TaskSchedulerTool(DynamicTaskScheduler scheduler, AgentDeliveryContext delivery) {
        this.scheduler = scheduler;
        this.delivery = delivery;
    }

    @Tool("Schedule a task that runs later and delivers its reply to a chat. Use it when the owner asks for a "
            + "reminder or a recurring job, such as \"remind me in two hours\" or \"every weekday at nine\". "
            + "Give exactly one timing: inMinutes for a one-time task that many minutes from now, or a Quartz cron "
            + "(seconds minutes hours day-of-month month day-of-week year, with ? in the day you do not use) for a "
            + "recurring task. A cron with a fixed day, month and year runs once on that date. In a chat, leave "
            + "providerId and chatId empty to deliver back to that chat; from the REST API both are required. The "
            + "task runs with the full agent and its tools, in the conversation that scheduled it. Schedules are "
            + "stored under the agent's state (as files with the markdown backend) and are registered again after "
            + "a restart. " + DURABILITY)
    public String scheduleTask(
            @P("What to do when the task runs, written as a complete instruction") String task,
            @P("Quartz cron for a recurring task, or a fixed date for a one-time task; empty when inMinutes is used")
            String cron,
            @P("Minutes from now for a one-time task; empty when cron is used") Integer inMinutes,
            @P("Messaging provider to deliver to, such as telegram or discord; empty to reuse this chat") String providerId,
            @P("Chat identifier to deliver to; empty to reuse this chat") String chatId) {
        String memoryId = delivery.memoryId();
        if (isBlank(memoryId)) {
            return error("there is no conversation to schedule this for");
        }
        try {
            TaskDestination destination = destinationFor(providerId, chatId);
            String resolvedCron = cronFor(cron, inMinutes);
            return scheduled(scheduler.create(memoryId, destination, task, resolvedCron));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @Tool("List the tasks scheduled so far, with the next run of each and where it delivers. A task that was due "
            + "while the agent was down is listed as missed and was not run.")
    public String listScheduledTasks() {
        List<TaskView> views = scheduler.list();
        if (views.isEmpty()) {
            return "No scheduled tasks.";
        }
        StringBuilder listing = new StringBuilder("Scheduled tasks (" + DURABILITY + "):");
        for (TaskView view : views) {
            ScheduledTask task = view.task();
            listing.append("\n- ").append(task.id())
                    .append(" | next run ").append(formatted(view.nextFire()))
                    .append(" | ").append(schedule(task))
                    .append(" | ").append(state(task))
                    .append(" | to ").append(task.destination().providerId()).append(':')
                    .append(task.destination().chatId())
                    .append(" | task: ").append(task.task());
        }
        return listing.toString();
    }

    @Tool("Cancel a scheduled task by its id, as returned when it was scheduled or listed. Cancelling also removes "
            + "a task that was marked missed or invalid.")
    public String cancelScheduledTask(@P("The task id, such as qlawkus-task-...") String taskId) {
        try {
            if (scheduler.cancel(taskId)) {
                return "Cancelled task " + taskId + ".";
            }
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
        return error("no task scheduled by the agent has id " + taskId);
    }

    private TaskDestination destinationFor(String providerId, String chatId) {
        boolean hasProvider = !isBlank(providerId);
        boolean hasChat = !isBlank(chatId);
        if (hasProvider != hasChat) {
            throw new IllegalArgumentException("providerId and chatId must be given together");
        }
        if (hasProvider) {
            if (REST_CHANNEL.equalsIgnoreCase(providerId.strip())) {
                throw new IllegalArgumentException("rest is not a messaging channel, so it cannot receive a task");
            }
            return new TaskDestination(providerId.strip(), chatId.strip());
        }
        if (delivery.hasDeliveryInfo()) {
            return new TaskDestination(delivery.providerId(), delivery.chatId());
        }
        throw new IllegalArgumentException("this conversation is not a chat, so providerId and chatId are required");
    }

    private String cronFor(String cron, Integer inMinutes) {
        boolean hasCron = !isBlank(cron);
        if (hasCron == (inMinutes != null)) {
            throw new IllegalArgumentException("give exactly one of cron or inMinutes");
        }
        return hasCron ? cron.strip() : scheduler.cronInMinutes(inMinutes);
    }

    private String scheduled(TaskView view) {
        return "Scheduled task " + view.task().id() + ". Next run " + formatted(view.nextFire())
                + " (" + schedule(view.task()) + "), delivering to " + view.task().destination().providerId()
                + ":" + view.task().destination().chatId() + ". " + DURABILITY;
    }

    private String schedule(ScheduledTask task) {
        return task.oneShot() ? "one-time" : "recurring " + task.cron();
    }

    private static String state(ScheduledTask task) {
        return switch (task.status()) {
            case ACTIVE -> "active";
            case MISSED -> MISSED_NOTE;
            case INVALID -> INVALID_NOTE;
        };
    }

    private String formatted(Instant instant) {
        if (instant == null) {
            return "none";
        }
        return instant.atZone(scheduler.zone()).toLocalDateTime().toString().replace('T', ' ')
                + " " + scheduler.zone().getId();
    }

    private static String error(String message) {
        return "Error: " + message + ".";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
