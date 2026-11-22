package dev.omatheusmesmo.qlawkus.task;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.function.Supplier;

/** Connects a scheduler firing to model events on the same worker. Delivery carries the run ID explicitly. */
@ApplicationScoped
public class TaskAudit {
    private static final ThreadLocal<UUID> CURRENT_RUN = new ThreadLocal<>();

    @Inject Event<TaskAuditEvent> events;

    public void record(TaskAuditEvent event) {
        try {
            events.fire(event);
        } catch (RuntimeException failure) {
            Log.warnf("Task history observer failed (%s)", failure.getClass().getSimpleName());
        }
    }

    public static UUID currentRun() {
        return CURRENT_RUN.get();
    }

    static <T> T callAs(UUID runId, Supplier<T> action) {
        UUID previous = CURRENT_RUN.get();
        CURRENT_RUN.set(runId);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT_RUN.remove();
            else CURRENT_RUN.set(previous);
        }
    }
}
