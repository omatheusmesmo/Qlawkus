package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import dev.omatheusmesmo.qlawkus.console.ConsoleFeatures;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/console/specialists")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class SpecialistsConsoleResource {

    private final Template page;
    private final ConsoleFeatures features;
    private final AdminAuthConfig admin;
    private final SecurityIdentity identity;

    public SpecialistsConsoleResource(@Location("console/specialists.html") Template page, ConsoleFeatures features,
            AdminAuthConfig admin, SecurityIdentity identity) {
        this.page = page;
        this.features = features;
        this.admin = admin;
        this.identity = identity;
    }

    @GET
    public TemplateInstance page() {
        if (!features.isSpecialists()) {
            throw new NotFoundException();
        }
        if (!admin.username().equals(identity.getPrincipal().getName())) {
            throw new ForbiddenException();
        }
        return page.data("active", "specialists");
    }
}
