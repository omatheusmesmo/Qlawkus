package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.ops.OpsDashboard;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Authenticated copy of the ops dashboard on the main HTTP port (for distributions without a
 * management listener or for quick checks in dev).
 */
@Path("/api/admin/ops")
@Authenticated
public class OpsDashboardResource {

    @Inject
    OpsDashboard dashboard;

    @GET
    @Path("dashboard")
    @Produces(MediaType.TEXT_HTML)
    public String dashboard() {
        return dashboard.renderHtml();
    }
}
