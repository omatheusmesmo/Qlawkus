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
    private static final String NO_CHAT_YET = "no chat to deliver to yet: write to me on Telegram or Discord first";

    private static final String REST_CHANNEL = "rest";

    private final DynamicTaskScheduler scheduler;
    private final AgentDeliveryContext delivery;
    private final OwnerChatDirectory ownerChats;

    @Inject
    public TaskSchedulerTool(DynamicTaskScheduler scheduler, AgentDeliveryContext delivery,
            OwnerChatDirectory ownerChats) {
        this.scheduler = scheduler;
        this.delivery = delivery;
        this.ownerChats = ownerChats;
    }

    @Tool("Schedule a task the agent runs later at the owner's request, and deliver its reply to a chat. Use it "
            + "for reminders and recurring jobs, such as \"remind me in 20 minutes\" or \"every weekday at nine\". "
            + "Give exactly one timing: inMinutes for a one-time task that many minutes from now (\"in 20 minutes\" "
            + "is inMinutes 20), or cron for a recurring task or one fixed date. Cron is Quartz: seconds minutes "
            + "hours day-of-month month day-of-week year, with ? in the day you do not use. Examples: every 5 "
            + "minutes is \"0 0/5 * * * ? *\"; every weekday at nine is \"0 0 9 ? * MON-FRI *\"; once at noon on 15 "
            + "June 2027 is \"0 0 12 15 6 ? 2027\". Cron runs in the agent's timezone (qlawkus.agent.timezone, "
            + "America/Sao_Paulo by default), so \"at nine\" means nine in that zone. The task text runs later, "
            + "without this conversation in view, so write it as a complete instruction with every detail it needs; "
            + "it must not ask the owner questions, because nobody answers them there. Where the reply goes: from a "
            + "messaging chat, back to that chat. From REST or the console, to the owner's most recent chat on the "
            + "provider named in providerId, or on any messaging provider when providerId is omitted; when the owner "
            + "has no such chat yet the result says so, and then the owner must write to you there first. The result "
            + "gives the task id, the next run, the timezone and the destination: tell the owner those. To confirm a "
            + "recurring task is really set, use listScheduledTasks rather than assuming it. " + DURABILITY)
    public String scheduleTask(
            @P("What to do when the task runs, as a complete self-contained instruction. It runs later without this "
                    + "conversation in view, so include every detail it needs. It must not ask the owner questions.")
            String task,
            @P(value = "Quartz cron for a recurring task or one fixed date, in the agent's timezone. Examples: "
                    + "\"0 0/5 * * * ? *\" every 5 minutes, \"0 0 9 ? * MON-FRI *\" weekdays at nine. Omit it (do not "
                    + "send an empty string or 0) when inMinutes is used.", required = false)
            String cron,
            @P(value = "Whole minutes from now for a one-time task, such as 20 for \"in 20 minutes\". Omit it (do not "
                    + "send an empty string or 0) when cron is used.", required = false)
            String inMinutes,
            @P(value = "Messaging provider name, telegram or discord, to deliver to the owner's most recent chat on it. "
                    + "Omit it (do not send an empty string) to deliver to this chat, or to the owner's most recent chat "
                    + "on any provider when this conversation is not a chat. Set it only when the owner names a provider; "
                    + "never guess one.", required = false)
            String providerId) {
        String memoryId = delivery.memoryId();
        if (isBlank(memoryId)) {
            return error("there is no conversation to schedule this for");
        }
        try {
            TaskDestination destination = destinationFor(providerId);
            String resolvedCron = cronFor(cron, inMinutes);
            return scheduled(scheduler.create(memoryId, destination, task, resolvedCron));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @Tool("List the tasks the agent has scheduled: each task's id, its next run, its schedule in the agent's timezone, "
            + "where its reply goes, and its task text. A task that was due while the agent was down is listed as "
            + "missed and was not run. Use it to confirm a recurring task is set, and to find the id that "
            + "cancelScheduledTask needs.")
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
                    .append(" | delivers to ").append(where(task.destination()))
                    .append(" | task: ").append(task.task());
        }
        return listing.toString();
    }

    @Tool("Cancel a scheduled task so it never runs again. Pass the id exactly as scheduleTask or listScheduledTasks "
            + "printed it; never make one up. Cancelling also removes a task marked missed or invalid. Tell the owner "
            + "the task is cancelled.")
    public String cancelScheduledTask(
            @P("The task id, such as qlawkus-task-..., copied from listScheduledTasks or scheduleTask; never invent one.")
            String taskId) {
        try {
            if (scheduler.cancel(taskId)) {
                return "Cancelled task " + taskId + ".";
            }
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }
        return error("no task scheduled by the agent has id " + taskId);
    }

    private TaskDestination destinationFor(String providerId) {
        String named = isBlank(providerId) ? null : providerId.strip();
        if (named != null && REST_CHANNEL.equalsIgnoreCase(named)) {
            throw new IllegalArgumentException("rest is not a messaging channel, so it cannot receive a task");
        }
        boolean fromChat = delivery.hasDeliveryInfo();
        if (fromChat && (named == null || named.equalsIgnoreCase(delivery.providerId()))) {
            return new TaskDestination(delivery.providerId(), delivery.chatId());
        }
        if (named == null) {
            return ownerChats.mostRecent().map(TaskSchedulerTool::destinationOf)
                    .orElseThrow(() -> new IllegalArgumentException(NO_CHAT_YET));
        }
        return ownerChats.lastChat(named).map(TaskSchedulerTool::destinationOf)
                .orElseThrow(() -> new IllegalArgumentException("no chat to deliver to on " + displayName(named)
                        + " yet: write to me there first"));
    }

    private String cronFor(String cron, String inMinutes) {
        boolean hasCron = !isBlank(cron);
        Long minutes = minutesOf(inMinutes);
        if (hasCron == (minutes != null)) {
            throw new IllegalArgumentException("give exactly one of cron or inMinutes");
        }
        return hasCron ? cron.strip() : scheduler.cronInMinutes(minutes);
    }

    private static Long minutesOf(String inMinutes) {
        if (isBlank(inMinutes)) {
            return null;
        }
        long minutes;
        try {
            minutes = Long.parseLong(inMinutes.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("inMinutes must be a whole number of minutes, such as 20");
        }
        return minutes == 0 ? null : minutes;
    }

    private String scheduled(TaskView view) {
        ScheduledTask task = view.task();
        return "Scheduled task " + task.id() + ". Next run " + formatted(view.nextFire())
                + " (" + schedule(task) + "), delivers to " + where(task.destination()) + ". " + DURABILITY;
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

    private static String where(TaskDestination destination) {
        return "your " + displayName(destination.providerId()) + " chat";
    }

    private static TaskDestination destinationOf(OwnerChat chat) {
        return new TaskDestination(chat.providerId(), chat.chatId());
    }

    private static String displayName(String provider) {
        return Character.toUpperCase(provider.charAt(0)) + provider.substring(1);
    }

    private static String error(String message) {
        return "Error: " + message + ".";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
