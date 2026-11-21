package dev.omatheusmesmo.qlawkus.tools.mcp;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Authenticated control plane; all validation, persistence and client lifecycle live in the registry. */
@Path("/api/admin/mcp/connections")
@Authenticated
@Blocking
@Produces(MediaType.APPLICATION_JSON)
public class McpAdminResource {
    @Inject
    McpRegistry registry;

    @GET
    public List<McpConnection> list() {
        return registry.list();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(@PathParam("id") String id, McpConnection connection) {
        if (connection == null || !id.equals(connection.id())) {
            return badRequest("Connection id must match the path");
        }
        return Response.ok(registry.put(connection)).build();
    }

    @POST
    @Path("/{id}/test")
    public Response test(@PathParam("id") String id) {
        return Response.ok(registry.test(id)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response remove(@PathParam("id") String id) {
        return registry.remove(id) ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    @ServerExceptionMapper
    public Response registryFailure(McpRegistryException failure) {
        return badRequest(failure.getMessage());
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", message)).build();
    }
}
