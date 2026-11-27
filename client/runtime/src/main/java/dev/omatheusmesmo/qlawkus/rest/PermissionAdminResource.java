package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.dto.PermissionAuditView;
import dev.omatheusmesmo.qlawkus.dto.PermissionChangeView;
import dev.omatheusmesmo.qlawkus.dto.PermissionExplanationView;
import dev.omatheusmesmo.qlawkus.dto.PermissionLevelRequest;
import dev.omatheusmesmo.qlawkus.dto.PermissionMatrixView;
import dev.omatheusmesmo.qlawkus.permission.PermissionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Map;

/**
 * The owner's view and control of the tool permission matrix. The authenticated admin may tighten or
 * loosen any cell directly: this is the owner path, so there is no approval step. A change answers 200
 * with its outcome, a rejected one 400 with an {@code error} message. Every endpoint is
 * {@code @Authenticated}; the model has no route to any of them and no tool exposes the service.
 */
@Path("/api/admin/permissions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PermissionAdminResource {

    private static final String ADMIN_ACTOR = "admin-api";
    private static final AgentDeliveryContext.Snapshot NO_ORIGIN = new AgentDeliveryContext.Snapshot(null, null, null);

    @Inject
    PermissionService permissions;

    @GET
    public PermissionMatrixView matrix() {
        return PermissionMatrixView.of(permissions.overview());
    }

    @PUT
    @Path("cells/{group}/{operation}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setCell(@PathParam("group") String group, @PathParam("operation") String operation,
            PermissionLevelRequest body, @Context SecurityContext security) {
        return respond(permissions.set(group, operation, level(body), actor(security), NO_ORIGIN));
    }

    @PUT
    @Path("tools/{tool}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setTool(@PathParam("tool") String tool, PermissionLevelRequest body,
            @Context SecurityContext security) {
        return respond(permissions.setTool(tool, level(body), actor(security), NO_ORIGIN));
    }

    @POST
    @Path("presets/{name}")
    public Response applyPreset(@PathParam("name") String name, @Context SecurityContext security) {
        return respond(permissions.applyPreset(name, actor(security), NO_ORIGIN));
    }

    @POST
    @Path("reset")
    public Response reset(@Context SecurityContext security) {
        return respond(permissions.reset(actor(security), NO_ORIGIN));
    }

    @GET
    @Path("explain/{tool}")
    public PermissionExplanationView explain(@PathParam("tool") String tool) {
        return PermissionExplanationView.of(permissions.explain(tool));
    }

    @GET
    @Path("audit")
    public List<PermissionAuditView> audit() {
        return permissions.audit().stream().map(PermissionAuditView::of).toList();
    }

    private static String level(PermissionLevelRequest body) {
        return body == null ? null : body.level();
    }

    private static PermissionService.Actor actor(SecurityContext security) {
        return PermissionService.Actor.admin(security.getUserPrincipal() == null
                ? ADMIN_ACTOR
                : ADMIN_ACTOR + ":" + security.getUserPrincipal().getName());
    }

    private static Response respond(PermissionService.Outcome outcome) {
        if (outcome.status() == PermissionService.Status.REJECTED) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", outcome.message())).build();
        }
        return Response.ok(PermissionChangeView.of(outcome)).build();
    }
}
