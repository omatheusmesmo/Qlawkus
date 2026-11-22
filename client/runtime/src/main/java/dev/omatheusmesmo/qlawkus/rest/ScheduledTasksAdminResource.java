package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Authenticated
@Path("/api/admin/scheduled-tasks")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduledTasksAdminResource {
    private final DynamicTaskScheduler tasks;

    public ScheduledTasksAdminResource(DynamicTaskScheduler tasks) {
        this.tasks = tasks;
    }

    @DELETE
    @Path("/{id}")
    public Response cancel(@PathParam("id") String id) {
        try {
            if (tasks.cancel(id)) {
                return Response.noContent().build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "No agent-scheduled task with that ID.")).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Could not remove the task. It stays scheduled.")).build();
        }
    }
}
