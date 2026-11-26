package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.dto.QuarantinedToolView;
import dev.omatheusmesmo.qlawkus.tool.quarantine.ToolQuarantine;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * The owner's view of the tools the agent has been stripped of for failing in a row, and the way to
 * hand one back before its cooldown ends. Releasing is the only write; there is no endpoint that
 * quarantines a tool, so the model has no route to either. A release answers 204, a tool that is not
 * quarantined 404.
 */
@Path("/api/admin/tool-quarantine")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ToolQuarantineAdminResource {

    @Inject
    ToolQuarantine quarantine;

    @GET
    public List<QuarantinedToolView> list() {
        return quarantine.quarantined().stream().map(QuarantinedToolView::of).toList();
    }

    @DELETE
    @Path("{tool}")
    public Response release(@PathParam("tool") String tool) {
        if (quarantine.release(tool)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "That tool is not quarantined"))
                .build();
    }
}
