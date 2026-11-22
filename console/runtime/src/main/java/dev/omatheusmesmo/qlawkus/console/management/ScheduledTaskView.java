package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import dev.omatheusmesmo.qlawkus.task.TaskView;
import io.quarkus.qute.TemplateData;

@TemplateData
public record ScheduledTaskView(String id, String description, String cron, String provider, String chat,
                                boolean oneShot, TaskStatus status, String nextFireTime) {
    static ScheduledTaskView of(TaskView view, String nextFireTime) {
        var task = view.task();
        return new ScheduledTaskView(task.id(), task.task(), task.cron(), task.destination().providerId(),
                task.destination().chatId(), task.oneShot(), task.status(), nextFireTime);
    }

    public String statusLabel() {
        return switch (status) {
            case ACTIVE -> "Active";
            case MISSED -> "Missed";
            case INVALID -> "Invalid";
        };
    }

    public boolean active() {
        return status == TaskStatus.ACTIVE;
    }
}
