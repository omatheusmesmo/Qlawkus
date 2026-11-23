package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/admin/heartbeat")
@Authenticated
@Blocking
@Produces(MediaType.APPLICATION_JSON)
public class HeartbeatAdminResource {

    private final HeartbeatService service;
    private final SecurityIdentity identity;
    private final AdminAuthConfig auth;

    public HeartbeatAdminResource(HeartbeatService service, SecurityIdentity identity, AdminAuthConfig auth) {
        this.service = service;
        this.identity = identity;
        this.auth = auth;
    }

    @GET
    public Response state() {
        requireOwner();
        return settingsResponse(service.status());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response configure(HeartbeatSettings settings) {
        requireOwner();
        if (settings == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Heartbeat settings are required.")).build();
        }
        try {
            return settingsResponse(service.configure(settings));
        } catch (IllegalArgumentException failure) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", failure.getMessage())).build();
        } catch (IllegalStateException failure) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Heartbeat settings could not be saved.")).build();
        }
    }

    private void requireOwner() {
        if (!auth.username().equals(identity.getPrincipal().getName())) {
            throw new ForbiddenException();
        }
    }

    private static Response settingsResponse(HeartbeatStatus status) {
        return Response.ok(status).header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
    }
}
