package dev.omatheusmesmo.qlawkus.specialists;

import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
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

@Path("/api/admin/specialists")
@Authenticated
@Blocking
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SpecialistAdminResource {

    private final SpecialistCatalog catalog;
    private final SpecialistRunner runner;
    private final SecurityIdentity identity;
    private final AdminAuthConfig admin;

    public SpecialistAdminResource(SpecialistCatalog catalog, SpecialistRunner runner, SecurityIdentity identity,
            AdminAuthConfig admin) {
        this.catalog = catalog;
        this.runner = runner;
        this.identity = identity;
        this.admin = admin;
    }

    @GET
    public Response list() {
        requireOwner();
        return Response.ok(new CatalogView(catalog.list(), catalog.models()))
                .header("Cache-Control", "private, no-store").build();
    }

    @POST
    public Response create(@Valid @NotNull SpecialistDefinition definition) {
        requireOwner();
        return Response.status(Response.Status.CREATED).entity(catalog.create(definition)).build();
    }

    @PUT
    @Path("/{id}")
    public SpecialistDefinition update(@PathParam("id") String id, @Valid @NotNull SpecialistDefinition definition) {
        requireOwner();
        if (!id.equals(definition.id())) {
            throw SpecialistProblem.invalid("The specialist id must match the URL.");
        }
        return catalog.update(definition);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        requireOwner();
        catalog.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/runs")
    public List<SpecialistRunner.Result> run(@NotNull RunRequest request) {
        requireOwner();
        return runner.run(request.pattern(), request.specialists(), request.snapshot());
    }

    @ServerExceptionMapper
    public Response specialistProblem(SpecialistProblem failure) {
        return Response.status(failure.status()).entity(Map.of("error", failure.getMessage())).build();
    }

    private void requireOwner() {
        if (!admin.username().equals(identity.getPrincipal().getName())) {
            throw new ForbiddenException();
        }
    }

    @RegisterForReflection
    record CatalogView(List<SpecialistDefinition> specialists, List<SpecialistsConfig.Tier> models) {
    }

    @RegisterForReflection
    record RunRequest(SpecialistRunner.Pattern pattern, List<String> specialists, String snapshot) {
    }
}
