package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.dto.AdminSetupRequest;
import dev.omatheusmesmo.qlawkus.security.AdminSetupService;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * The first-run admin setup API, the only admin surface open without credentials, because before the
 * password exists there are none. It is open only while no admin password is set: afterwards
 * {@code POST} answers {@code 409} and {@code GET} reports that setup is no longer required. Setting the
 * password also needs the one-time token printed in the agent log, so reaching the port is not enough.
 *
 * <p>{@code 403} means a wrong token, {@code 400} an unacceptable password, {@code 409} that the password
 * is already set, and {@code 503} that it could not be stored (setup stays open and nothing changed).
 */
@Path("/api/admin/setup")
@PermitAll
@IfBuildProperty(name = "qlawkus.admin.argon2.enabled", stringValue = "true")
public class AdminSetupResource {

  @Inject
  AdminSetupService setup;

  @GET
  public Map<String, Boolean> status() {
    return Map.of("required", setup.required());
  }

  @POST
  @Path("/password")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setPassword(@Valid AdminSetupRequest request) {
    setup.complete(request.token(), request.password().toCharArray());
    return Response.noContent().build();
  }

  @ServerExceptionMapper
  public Response closed(AdminSetupService.SetupClosedException failure) {
    return error(Response.Status.CONFLICT, failure.getMessage());
  }

  @ServerExceptionMapper
  public Response wrongToken(AdminSetupService.InvalidTokenException failure) {
    return error(Response.Status.FORBIDDEN, failure.getMessage());
  }

  @ServerExceptionMapper
  public Response notStored(AdminSetupService.NotPersistedException failure) {
    return error(Response.Status.SERVICE_UNAVAILABLE, failure.getMessage());
  }

  @ServerExceptionMapper
  public Response unacceptable(IllegalArgumentException failure) {
    return error(Response.Status.BAD_REQUEST, failure.getMessage());
  }

  private static Response error(Response.Status status, String message) {
    return Response.status(status).entity(Map.of("error", message)).build();
  }
}
