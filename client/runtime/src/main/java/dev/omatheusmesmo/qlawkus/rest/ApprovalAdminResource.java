package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalService;
import dev.omatheusmesmo.qlawkus.dto.ApprovalView;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The owner's view of the approval queue. Inspection and the two decisions are the only operations:
 * there is deliberately no endpoint that runs a tool, so a decision can only release a call the owner
 * already saw recorded. A successful decision answers 204 and the record is read back with GET; a refused
 * one answers 409 and an unknown id 404, both with an {@code error} message. Every endpoint is
 * {@code @Authenticated}; the model has no route to any of them.
 */
@Path("/api/admin/approvals")
@Authenticated
public class ApprovalAdminResource {

    private static final String ADMIN_ACTOR = "admin-api";

    @Inject
    ApprovalService approvals;

    @GET
    public List<ApprovalView> list() {
        return approvals.list().stream().map(ApprovalView::summary).toList();
    }

    @GET
    @Path("{id}")
    public Response get(@PathParam("id") String id) {
        if (!ApprovalRequest.isValidId(id)) {
            return error(Response.Status.BAD_REQUEST, "Approval ids are 32 lower-case hex characters");
        }
        Optional<ApprovalRequest> found = approvals.find(id);
        return found.isPresent()
                ? Response.ok(ApprovalView.detail(found.get())).build()
                : error(Response.Status.NOT_FOUND, "No approval request with that id");
    }

    @POST
    @Path("{id}/approve")
    public Response approve(@PathParam("id") String id, @Context SecurityContext security) {
        return decide(id, security, true);
    }

    @POST
    @Path("{id}/deny")
    public Response deny(@PathParam("id") String id, @Context SecurityContext security) {
        return decide(id, security, false);
    }

    private Response decide(String id, SecurityContext security, boolean approve) {
        if (!ApprovalRequest.isValidId(id)) {
            return error(Response.Status.BAD_REQUEST, "Approval ids are 32 lower-case hex characters");
        }
        String actor = security.getUserPrincipal() == null
                ? ADMIN_ACTOR
                : ADMIN_ACTOR + ":" + security.getUserPrincipal().getName();
        ApprovalService.Decision decision = approve ? approvals.approve(id, actor) : approvals.deny(id, actor);
        if (decision.request() == null) {
            return error(Response.Status.NOT_FOUND, decision.message());
        }
        if (!decision.accepted()) {
            return error(Response.Status.CONFLICT, decision.message());
        }
        return Response.noContent().build();
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(Map.of("error", message)).build();
    }
}
