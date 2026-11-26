package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.routine.Routine;
import dev.omatheusmesmo.qlawkus.routine.RoutineOutcome;
import dev.omatheusmesmo.qlawkus.routine.RoutineRunner;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Lets the owner run a routine now to see what it says, whether or not it is switched on. The report
 * goes to the chat the owner last wrote from, exactly as a scheduled run would.
 */
@Path("/api/admin/routines")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class RoutinesAdminResource {

    @Inject
    RoutineRunner routines;

    @POST
    @Path("{name}/run")
    public Response run(@PathParam("name") String name) {
        return Routine.of(name).map(routine -> {
            RoutineOutcome outcome = routines.runNow(routine);
            return Response.ok(Map.of("routine", routine.slug(), "outcome", outcome.name())).build();
        }).orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Unknown routine. Use weekly or morning.")).build());
    }
}
