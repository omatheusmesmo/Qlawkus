package dev.omatheusmesmo.qlawkus.rest;

import dev.omatheusmesmo.qlawkus.compose.CompositionAdminService;
import dev.omatheusmesmo.qlawkus.composition.InvalidManifestException;
import dev.omatheusmesmo.qlawkus.dto.CompositionState;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

/**
 * Admin control plane for the composition manifest: stage a new {@code agent.yml} for the next
 * rebuild and read what is active/staged. The endpoint never builds - it only records intent, which
 * an external builder acts on. That keeps it safe by construction: authenticated, and unable to
 * execute anything beyond writing a validated manifest.
 */
@Path("/api/admin/composition")
@Authenticated
public class CompositionAdminResource {

  @Inject
  CompositionAdminService service;

  @POST
  @Path("/manifest")
  @Consumes({MediaType.TEXT_PLAIN, "application/yaml", "application/x-yaml"})
  public Response stage(String yaml) throws IOException {
    if (yaml == null || yaml.isBlank()) {
      return badRequest("request body must be an agent.yml document");
    }
    try {
      service.stage(yaml);
    } catch (InvalidManifestException e) {
      return badRequest(e.getMessage());
    }
    return Response.ok(service.currentState()).build();
  }

  @GET
  public CompositionState state() throws IOException {
    return service.currentState();
  }

  @DELETE
  @Path("/manifest")
  public Response discard() throws IOException {
    return service.discardStaged()
        ? Response.noContent().build()
        : Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "no staged manifest to discard"))
            .build();
  }

  private static Response badRequest(String message) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(Map.of("error", message))
        .build();
  }
}
