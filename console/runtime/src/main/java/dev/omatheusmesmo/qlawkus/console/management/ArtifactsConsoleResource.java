package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactHandle;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateData;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

@Path("/console/artifacts")
@Authenticated
@Produces(MediaType.TEXT_HTML)
@Blocking
@TemplateData(target = ArtifactHandle.class)
public class ArtifactsConsoleResource {

    private final Template page;
    private final Instance<ArtifactLibrary> library;
    private final SecurityIdentity identity;

    public ArtifactsConsoleResource(@Location("console/artifacts.html") Template page, Instance<ArtifactLibrary> library,
            SecurityIdentity identity) {
        this.page = page;
        this.library = library;
        this.identity = identity;
    }

    @GET
    public Response page() {
        if (!library.isResolvable()) {
            throw new NotFoundException();
        }
        try {
            var files = library.get().list(identity.getPrincipal().getName());
            return Response.ok(page.data("active", "artifacts", "files", files).render())
                    .header("Cache-Control", "private, no-store")
                    .build();
        } catch (IOException unavailable) {
            throw new ServiceUnavailableException();
        }
    }
}
