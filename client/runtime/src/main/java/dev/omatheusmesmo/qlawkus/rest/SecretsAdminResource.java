package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.dto.SetSecretRequest;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin API to onboard secrets into the encrypted keystore without {@code keytool}. Every endpoint is
 * basic-auth gated ({@code @Authenticated}, admin role) and the secret value is never echoed back or
 * logged. A written secret takes effect on the next boot, since config sources are read at startup.
 *
 * <p>A missing {@code qlawkus.secrets.keystore-password} maps to {@code 409 Conflict} (the store
 * cannot be unlocked), and invalid input to {@code 400 Bad Request}.
 */
@Path("/api/admin/secrets")
@Authenticated
public class SecretsAdminResource {

  @Inject
  KeystoreSecretWriter writer;

  @GET
  public Response list() {
    return guarded(() -> Response.ok(Map.of("aliases", writer.aliases())).build());
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response set(@Valid SetSecretRequest request) {
    return guarded(() -> {
      writer.setSecret(request.alias(), request.value());
      return Response.noContent().build();
    });
  }

  @DELETE
  public Response delete(@QueryParam("alias") String alias) {
    if (alias == null || alias.isBlank()) {
      return error(Response.Status.BAD_REQUEST, "Specify the alias to delete");
    }
    return guarded(() -> writer.deleteSecret(alias)
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND).build());
  }

  private Response guarded(Supplier<Response> action) {
    try {
      return action.get();
    } catch (IllegalStateException e) {
      return error(Response.Status.CONFLICT, e.getMessage());
    } catch (IllegalArgumentException e) {
      return error(Response.Status.BAD_REQUEST, e.getMessage());
    }
  }

  private static Response error(Response.Status status, String message) {
    return Response.status(status).entity(Map.of("error", message)).build();
  }
}
