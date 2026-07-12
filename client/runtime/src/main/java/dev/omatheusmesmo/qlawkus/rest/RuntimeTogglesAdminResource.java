package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.config.RuntimeToggleWriter;
import dev.omatheusmesmo.qlawkus.dto.SetRuntimeToggleRequest;
import dev.omatheusmesmo.qlawkus.secrets.SecretPropertyCatalog;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin API for the config editor's {@code RUN_TIME} tier: write a single property into the
 * runtime-toggle external override file, applied on the next restart. Mirrors
 * {@link SecretsAdminResource} in shape; a property listed in {@link SecretPropertyCatalog} is
 * rejected here since it must go through the encrypted keystore instead.
 */
@Path("/api/admin/runtime-toggles")
@Authenticated
public class RuntimeTogglesAdminResource {

  @Inject
  RuntimeToggleWriter writer;

  @GET
  public Map<String, String> list() {
    return writer.all();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response set(@Valid SetRuntimeToggleRequest request) {
    return set(request.property(), request.value());
  }

  /** Form-encoded variant of {@link #set(SetRuntimeToggleRequest)}, for the console's HTMX form. */
  @PUT
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response setForm(@FormParam("property") String property, @FormParam("value") String value) {
    return set(property, value);
  }

  private Response set(String property, String value) {
    return guarded(() -> {
      if (SecretPropertyCatalog.isSecret(property)) {
        return error(Response.Status.BAD_REQUEST,
            "%s is a secret; store it via PUT /api/admin/secrets instead".formatted(property));
      }
      writer.setToggle(property, value);
      return Response.noContent().build();
    });
  }

  @DELETE
  public Response delete(@QueryParam("property") String property) {
    if (property == null || property.isBlank()) {
      return error(Response.Status.BAD_REQUEST, "Specify the property to delete");
    }
    return guarded(() -> writer.deleteToggle(property)
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND).build());
  }

  private Response guarded(Supplier<Response> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException e) {
      return error(Response.Status.BAD_REQUEST, e.getMessage());
    }
  }

  private static Response error(Response.Status status, String message) {
    return Response.status(status).entity(Map.of("error", message)).build();
  }
}
