package dev.omatheusmesmo.qlawkus.artifacts;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Path("/api/artifacts")
@Authenticated
@Blocking
public class ArtifactResource {

    private final ArtifactLibrary library;
    private final SecurityIdentity identity;

    public ArtifactResource(ArtifactLibrary library, SecurityIdentity identity) {
        this.library = library;
        this.identity = identity;
    }

    @GET
    @Path("/{id}")
    public Response download(@PathParam("id") String id) {
        return serve(id, false);
    }

    @GET
    @Path("/{id}/preview")
    public Response preview(@PathParam("id") String id) {
        return serve(id, true);
    }

    private Response serve(String value, boolean preview) {
        var id = LocalArtifactStore.parseId(value);
        if (id == null) {
            return privateResponse(Response.status(Response.Status.NOT_FOUND));
        }
        try {
            var content = library.read(id, identity.getPrincipal().getName());
            if (content.isEmpty()) {
                return privateResponse(Response.status(Response.Status.NOT_FOUND));
            }
            var file = content.get();
            var response = Response.ok(file.bytes(), (preview ? "text/plain" : file.handle().mediaType()) + "; charset=UTF-8");
            if (!preview) {
                String name = URLEncoder.encode(file.handle().name(), StandardCharsets.UTF_8).replace("+", "%20");
                response.header("Content-Disposition", "attachment; filename*=UTF-8''" + name);
            }
            return privateResponse(response);
        } catch (IOException unavailable) {
            return privateResponse(Response.status(Response.Status.SERVICE_UNAVAILABLE));
        }
    }

    private static Response privateResponse(Response.ResponseBuilder response) {
        return response.header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .build();
    }
}
