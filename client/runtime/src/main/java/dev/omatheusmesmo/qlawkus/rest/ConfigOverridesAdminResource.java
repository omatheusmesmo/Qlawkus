package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.compose.ConfigOverridesAdminService;
import dev.omatheusmesmo.qlawkus.config.InvalidConfigOverrideException;
import dev.omatheusmesmo.qlawkus.dto.ConfigOverridesState;
import dev.omatheusmesmo.qlawkus.dto.SetConfigOverrideRequest;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Admin control plane for the config editor's {@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED}
 * tier: stage a new {@code .properties} override document for the next rebuild and read what is
 * active/staged. Mirrors {@link CompositionAdminResource} exactly - it never builds, only records
 * intent for the external redeploy loop to promote and act on.
 */
@Path("/api/admin/config-overrides")
@Authenticated
public class ConfigOverridesAdminResource {

  @Inject
  ConfigOverridesAdminService service;

  @POST
  @Path("/overrides")
  @Consumes({MediaType.TEXT_PLAIN, "text/x-java-properties"})
  public Response stage(String properties) throws IOException {
    try {
      service.stage(properties);
    } catch (InvalidConfigOverrideException e) {
      return badRequest(e.getMessage());
    }
    return Response.ok(service.currentState()).build();
  }

  @GET
  public ConfigOverridesState state() throws IOException {
    return service.currentState();
  }

  @DELETE
  @Path("/overrides")
  public Response discard() throws IOException {
    return service.discardStaged()
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "no staged config overrides to discard"))
            .build();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setOne(@Valid SetConfigOverrideRequest request) throws IOException {
    return setOne(request.property(), request.value());
  }

  /** Form-encoded variant of {@link #setOne(SetConfigOverrideRequest)}, for the console's HTMX form. */
  @PUT
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response setOneForm(@FormParam("property") String property, @FormParam("value") String value)
      throws IOException {
    return setOne(property, value);
  }

  private Response setOne(String property, String value) throws IOException {
    return guarded(() -> {
      service.stageOne(property, value);
      return Response.ok(service.currentState()).build();
    });
  }

  @DELETE
  public Response discardOne(@QueryParam("property") String property) throws IOException {
    if (property == null || property.isBlank()) {
      return badRequest("Specify the property to discard");
    }
    return service.discardOne(property)
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND).build();
  }

  private Response guarded(IoSupplier action) throws IOException {
    try {
      return action.get();
    } catch (InvalidConfigOverrideException e) {
      return badRequest(e.getMessage());
    }
  }

  @FunctionalInterface
  private interface IoSupplier {
    Response get() throws IOException;
  }

  private static Response badRequest(String message) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(Map.of("error", message))
        .build();
  }
}
