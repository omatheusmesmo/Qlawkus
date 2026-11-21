package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistry;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistryException;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** External resource consent reuses the console's owner authentication, not a second console login. */
@Path("/api/admin/mcp")
@Authenticated
@Blocking
@Produces(MediaType.APPLICATION_JSON)
public class McpOAuthResource {
    @Inject McpRegistry registry;
    @Inject McpOAuthService oauth;
    @Inject SecurityIdentity identity;

    @POST
    @Path("/connections/{id}/auth/begin")
    public Response begin(@PathParam("id") String id) {
        return response(oauth.begin(connection(id), identity.getPrincipal().getName()));
    }

    @GET
    @Path("/connections/{id}/auth")
    public Response status(@PathParam("id") String id) {
        return response(oauth.status(connection(id)));
    }

    @DELETE
    @Path("/connections/{id}/auth")
    public Response disconnect(@PathParam("id") String id) {
        McpConnection current = connection(id);
        registry.put(disabled(current));
        return response(oauth.disconnect(current));
    }

    @GET
    @Path("/oauth/callback")
    public Response callback(@QueryParam("state") String state, @QueryParam("code") String code,
            @QueryParam("error") String providerError) {
        McpConnection current = connection(oauth.connectionForState(state));
        oauth.complete(state, providerError == null ? code : null, identity.getPrincipal().getName(), current);
        return Response.seeOther(URI.create("/console/mcp"))
                .header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    @ServerExceptionMapper
    public Response registryFailure(McpRegistryException failure) {
        return error();
    }

    private McpConnection connection(String id) {
        return registry.list().stream().filter(connection -> connection.id().equals(id)).findFirst()
                .orElseThrow(() -> new McpRegistryException("Unknown MCP connection"));
    }

    static McpConnection disabled(McpConnection current) {
        return new McpConnection(current.id(), current.transport(), false, current.url(), current.command(),
                current.headerSecretRefs(), current.environmentSecretRefs(), current.protocolVersion(), current.oauth());
    }

    private static Response response(Object body) {
        return Response.ok(body).header("Cache-Control", "no-store").build();
    }

    private static Response error() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "MCP authorization could not be completed"))
                .header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").build();
    }
}
